package com.audioagent.contentanalysis.client;

import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.AiChatMessage;
import com.audioagent.ai.AiChatRequest;
import com.audioagent.ai.AiChatResponse;
import com.audioagent.ai.exception.AiAuthenticationException;
import com.audioagent.ai.exception.AiClientException;
import com.audioagent.ai.exception.AiRateLimitException;
import com.audioagent.ai.exception.AiPaymentRequiredException;
import com.audioagent.ai.exception.AiRequestException;
import com.audioagent.ai.exception.AiResponseException;
import com.audioagent.ai.exception.AiTimeoutException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class DeepSeekClientAdapter implements DeepSeekClient {

    private final AiChatClient aiChatClient;
    private final DeepSeekProperties properties;

    public DeepSeekClientAdapter(AiChatClient aiChatClient,
                                 DeepSeekProperties properties) {
        this.aiChatClient = aiChatClient;
        this.properties = properties;
    }

    @Override
    public DeepSeekResponse complete(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_SERVICE_NOT_CONFIGURED, false,
                    ErrorCode.AI_SERVICE_NOT_CONFIGURED.getMessage());
        }
        try {
            AiChatResponse response = aiChatClient.chat(new AiChatRequest(
                    properties.getModel(),
                    List.of(AiChatMessage.system(systemPrompt),
                            AiChatMessage.user(userPrompt)),
                    properties.getTemperature(),
                    properties.getMaxTokens(),
                    Map.of("type", "json_object"),
                    Duration.ofSeconds(properties.getReadTimeoutSeconds())));
            return new DeepSeekResponse(response.content(), response.model(),
                    response.promptTokens(), response.completionTokens(),
                    response.totalTokens());
        } catch (AiAuthenticationException e) {
            throw failure(ErrorCode.AI_AUTH_FAILED, e);
        } catch (AiPaymentRequiredException e) {
            throw failure(ErrorCode.AI_BALANCE_INSUFFICIENT, e);
        } catch (AiRateLimitException e) {
            throw new ContentAnalysisException(ErrorCode.AI_RATE_LIMITED,
                    true, "AI service is busy; please retry later",
                    e.getRetryAfterMilliseconds(), e);
        } catch (AiTimeoutException e) {
            throw failure(ErrorCode.AI_TIMEOUT, e);
        } catch (AiRequestException e) {
            throw failure(ErrorCode.AI_REQUEST_INVALID, e);
        } catch (AiResponseException e) {
            throw failure(ErrorCode.AI_RESPONSE_INVALID, e);
        } catch (AiClientException e) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE, e.isRetryable(),
                    "AI service is temporarily unavailable",
                    e.getRetryAfterMilliseconds(), e);
        }
    }

    private ContentAnalysisException failure(ErrorCode code,
                                              AiClientException cause) {
        return new ContentAnalysisException(code, cause.isRetryable(),
                code.getMessage(), cause.getRetryAfterMilliseconds(), cause);
    }
}
