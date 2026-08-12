package com.audioagent.ai.exception;

public class AiRequestException extends AiClientException {

    public AiRequestException(String message) {
        super(message, false);
    }
}
