package com.audioagent.ai.exception;

public class AiTimeoutException extends AiClientException {

    public AiTimeoutException(String message, Throwable cause) {
        super(message, true, cause);
    }
}
