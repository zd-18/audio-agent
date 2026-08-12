package com.audioagent.ai.exception;

public class AiRateLimitException extends AiClientException {

    public AiRateLimitException(String message, Long retryAfterMilliseconds) {
        super(message, true, retryAfterMilliseconds, null);
    }
}
