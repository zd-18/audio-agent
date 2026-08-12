package com.audioagent.ai.exception;

public class AiPaymentRequiredException extends AiClientException {

    public AiPaymentRequiredException(String message) {
        super(message, false);
    }
}
