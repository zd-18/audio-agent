package com.audioagent.ai.exception;

public class AiResponseException extends AiClientException {

    public AiResponseException(String message) {
        super(message, false);
    }

    public AiResponseException(String message, Throwable cause) {
        super(message, false, cause);
    }
}
