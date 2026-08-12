package com.audioagent.ai.exception;

import lombok.Getter;

@Getter
public class AiClientException extends RuntimeException {

    private final boolean retryable;
    private final Long retryAfterMilliseconds;

    public AiClientException(String message, boolean retryable) {
        this(message, retryable, null, null);
    }

    public AiClientException(String message, boolean retryable,
                             Throwable cause) {
        this(message, retryable, null, cause);
    }

    public AiClientException(String message, boolean retryable,
                             Long retryAfterMilliseconds,
                             Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.retryAfterMilliseconds = retryAfterMilliseconds;
    }
}
