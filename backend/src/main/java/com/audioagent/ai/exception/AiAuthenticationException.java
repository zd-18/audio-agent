package com.audioagent.ai.exception;

public class AiAuthenticationException extends AiClientException {

    public AiAuthenticationException(String message) {
        super(message, false);
    }
}
