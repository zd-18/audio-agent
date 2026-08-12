package com.audioagent.ai;

import com.audioagent.ai.exception.AiAuthenticationException;
import com.audioagent.ai.exception.AiClientException;
import com.audioagent.ai.exception.AiRateLimitException;
import com.audioagent.ai.exception.AiPaymentRequiredException;
import com.audioagent.ai.exception.AiRequestException;
import com.audioagent.ai.exception.AiResponseException;
import com.audioagent.ai.exception.AiTimeoutException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Slf4j
public class HttpAiChatClient implements AiChatClient {

    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final int RESPONSE_BODY_LOG_LIMIT = 1000;

    private final URI completionsUri;
    private final Supplier<String> apiKeySupplier;
    private final BooleanSupplier configuredSupplier;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpAiChatClient(String baseUrl,
                            Supplier<String> apiKeySupplier,
                            BooleanSupplier configuredSupplier,
                            ObjectMapper objectMapper,
                            Duration connectTimeout) {
        this.completionsUri = URI.create(stripTrailingSlash(baseUrl)
                + COMPLETIONS_PATH);
        this.apiKeySupplier = apiKeySupplier;
        this.configuredSupplier = configuredSupplier;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        validate(request);
        if (!configuredSupplier.getAsBoolean()) {
            throw new AiAuthenticationException(
                    "AI service is not configured");
        }
        String apiKey = apiKeySupplier.get();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(completionsUri)
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body(request))))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw forStatus(response.statusCode(),
                        response.headers().firstValue("Retry-After")
                                .orElse(null));
            }
            String responseBody = response.body();
            try {
                return parse(responseBody);
            } catch (JsonProcessingException e) {
                log.warn("AI response JSON parsing failed, status={}, "
                                + "responseBodyLength={}, "
                                + "responseBodyPrefix={}",
                        response.statusCode(), responseBody.length(),
                        responseBodyPrefix(responseBody));
                throw e;
            }
        } catch (AiClientException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AiTimeoutException("AI request timed out", e);
        } catch (ConnectException e) {
            throw new AiClientException(
                    "AI service is unavailable", false, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException(
                    "AI request was interrupted", true, e);
        } catch (JsonProcessingException e) {
            throw new AiResponseException(
                    "AI response could not be decoded", e);
        } catch (IOException e) {
            throw new AiClientException(
                    "AI service is unavailable", false, e);
        }
    }

    private void validate(AiChatRequest request) {
        if (request == null || request.model() == null
                || request.model().isBlank()
                || request.messages() == null
                || request.messages().isEmpty()
                || request.maxTokens() <= 0
                || request.timeout() == null
                || request.timeout().isNegative()
                || request.timeout().isZero()) {
            throw new AiRequestException("AI request is invalid");
        }
        for (AiChatMessage message : request.messages()) {
            if (message == null || message.role() == null
                    || !List.of("system", "user", "assistant")
                    .contains(message.role())
                    || message.content() == null
                    || message.content().isBlank()) {
                throw new AiRequestException("AI message is invalid");
            }
        }
    }

    private Map<String, Object> body(AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", request.messages());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        if (request.responseFormat() != null
                && !request.responseFormat().isEmpty()) {
            body.put("response_format", request.responseFormat());
        }
        return body;
    }

    private AiChatResponse parse(String raw) throws JsonProcessingException {
        ResponseBody response = objectMapper.readValue(raw,
                ResponseBody.class);
        List<Choice> choices = response == null ? null : response.choices();
        Choice firstChoice = choices == null || choices.isEmpty()
                ? null : choices.getFirst();
        MessageContent message = firstChoice == null
                ? null : firstChoice.message();
        String content = message == null ? null : message.content();
        if (response == null || choices == null || choices.isEmpty()
                || firstChoice == null || message == null
                || content == null || content.isBlank()) {
            log.warn("AI response is incomplete, responseBodyLength={}, "
                            + "model={}, choicesCount={}, "
                            + "firstChoicePresent={}, messagePresent={}, "
                            + "contentNull={}, contentBlank={}, "
                            + "contentLength={}, finishReason={}, "
                            + "usagePresent={}, responseBodyPrefix={}",
                    raw.length(), response == null ? null : response.model(),
                    choices == null ? 0 : choices.size(),
                    firstChoice != null, message != null,
                    content == null,
                    content != null && content.isBlank(),
                    content == null ? null : content.length(),
                    firstChoice == null ? null : firstChoice.finishReason(),
                    response != null && response.usage() != null,
                    responseBodyPrefix(raw));
            throw new AiResponseException("AI response is incomplete");
        }
        Usage usage = response.usage();
        return new AiChatResponse(
                response.choices().getFirst().message().content(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens(),
                response.model());
    }

    private String responseBodyPrefix(String responseBody) {
        return responseBody.substring(0,
                        Math.min(responseBody.length(),
                                RESPONSE_BODY_LOG_LIMIT))
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    private AiClientException forStatus(int status, String retryAfter) {
        return switch (status) {
            case 400, 422 -> new AiRequestException("AI request was rejected");
            case 401, 403 -> new AiAuthenticationException(
                    "AI authentication failed");
            case 402 -> new AiPaymentRequiredException(
                    "AI account balance is insufficient");
            case 408 -> new AiTimeoutException("AI request timed out", null);
            case 429 -> new AiRateLimitException(
                    "AI rate limit exceeded", parseRetryAfter(retryAfter));
            case 500, 502, 503, 504 -> new AiClientException(
                    "AI service is unavailable", true,
                    parseRetryAfter(retryAfter), null);
            default -> new AiClientException(
                    "AI service request failed", status >= 500);
        };
    }

    private Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return clamp(Duration.ofSeconds(
                    Long.parseLong(value.trim())).toMillis());
        } catch (NumberFormatException ignored) {
            try {
                return clamp(Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value,
                                DateTimeFormatter.RFC_1123_DATE_TIME))
                        .toMillis());
            } catch (RuntimeException ignoredDate) {
                return null;
            }
        }
    }

    private long clamp(long milliseconds) {
        return Math.max(100L, Math.min(milliseconds, 300_000L));
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseBody(List<Choice> choices, Usage usage,
                                String model) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(
            MessageContent message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageContent(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
