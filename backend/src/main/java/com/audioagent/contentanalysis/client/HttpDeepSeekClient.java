package com.audioagent.contentanalysis.client;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class HttpDeepSeekClient implements DeepSeekClient {

    private static final String COMPLETIONS_URI = "/chat/completions";

    private final RestClient restClient;
    private final DeepSeekProperties properties;

    public HttpDeepSeekClient(RestClient restClient,
                              DeepSeekProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public DeepSeekResponse complete(String systemPrompt,
                                     String userPrompt) {
        if (!properties.isConfigured()) {
            throw failure(ErrorCode.AI_SERVICE_NOT_CONFIGURED, false,
                    "智能分析服务尚未配置");
        }
        RequestBody body = new RequestBody(
                properties.getModel(),
                List.of(new Message("system", systemPrompt),
                        new Message("user", userPrompt)),
                Map.of("type", "json_object"),
                properties.getTemperature(),
                properties.getMaxTokens());
        try {
            ResponseBody response = restClient.post()
                    .uri(COMPLETIONS_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (request, httpResponse) -> {
                                throw forStatus(
                                        httpResponse.getStatusCode().value(),
                                        httpResponse.getHeaders()
                                                .getFirst("Retry-After"));
                            })
                    .body(ResponseBody.class);
            return requireResponse(response);
        } catch (ContentAnalysisException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (hasCause(e, SocketTimeoutException.class)) {
                throw failure(ErrorCode.AI_TIMEOUT, true,
                        "智能分析服务响应超时", e);
            }
            if (hasCause(e, ConnectException.class)) {
                throw failure(ErrorCode.AI_SERVICE_UNAVAILABLE, false,
                        "智能分析服务暂时不可用", e);
            }
            throw failure(ErrorCode.AI_SERVICE_UNAVAILABLE, false,
                    "智能分析服务暂时不可用", e);
        } catch (RestClientException e) {
            throw failure(ErrorCode.AI_RESPONSE_INVALID, false,
                    "智能分析服务返回了无法识别的响应", e);
        }
    }

    private DeepSeekResponse requireResponse(ResponseBody response) {
        if (response == null || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().getFirst() == null
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null
                || response.choices().getFirst().message().content()
                .isBlank()) {
            throw failure(ErrorCode.AI_RESPONSE_INVALID, false,
                    "智能分析服务返回了不完整的结果");
        }
        Usage usage = response.usage();
        return new DeepSeekResponse(
                response.choices().getFirst().message().content(),
                response.model(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens());
    }

    private ContentAnalysisException forStatus(
            int status, String retryAfter) {
        return switch (status) {
            case 400, 422 -> failure(ErrorCode.AI_REQUEST_INVALID, false,
                    "智能分析请求不合法");
            case 401, 403 -> failure(ErrorCode.AI_AUTH_FAILED, false,
                    "智能分析服务认证失败");
            case 402 -> failure(ErrorCode.AI_BALANCE_INSUFFICIENT, false,
                    "智能分析服务余额不足");
            case 408 -> failure(ErrorCode.AI_TIMEOUT, true,
                    "智能分析服务响应超时");
            case 429 -> new ContentAnalysisException(
                    ErrorCode.AI_RATE_LIMITED, true,
                    "智能分析请求过于频繁，请稍后重试",
                    parseRetryAfter(retryAfter), null);
            case 500, 502, 503, 504 -> new ContentAnalysisException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE, true,
                    "智能分析服务暂时不可用",
                    parseRetryAfter(retryAfter), null);
            default -> failure(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    status >= 500,
                    "智能分析服务暂时不可用");
        };
    }

    private Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return clampDelay(Duration.ofSeconds(seconds).toMillis());
        } catch (NumberFormatException ignored) {
            try {
                long millis = Duration.between(
                        ZonedDateTime.now(),
                        ZonedDateTime.parse(value,
                                DateTimeFormatter.RFC_1123_DATE_TIME))
                        .toMillis();
                return clampDelay(millis);
            } catch (RuntimeException ignoredDate) {
                return null;
            }
        }
    }

    private long clampDelay(long milliseconds) {
        return Math.max(100L, Math.min(milliseconds, 300_000L));
    }

    private ContentAnalysisException failure(
            ErrorCode code, boolean retryable, String message) {
        return new ContentAnalysisException(code, retryable, message);
    }

    private ContentAnalysisException failure(
            ErrorCode code, boolean retryable, String message,
            Throwable cause) {
        return new ContentAnalysisException(code, retryable, message, cause);
    }

    private boolean hasCause(Throwable error,
                             Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record RequestBody(
            String model,
            List<Message> messages,
            @JsonProperty("response_format")
            Map<String, String> responseFormat,
            double temperature,
            @JsonProperty("max_tokens")
            int maxTokens) {
    }

    private record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseBody(
            List<Choice> choices,
            Usage usage,
            String model) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(MessageContent message) {
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
