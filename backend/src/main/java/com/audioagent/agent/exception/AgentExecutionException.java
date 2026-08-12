package com.audioagent.agent.exception;

import com.audioagent.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class AgentExecutionException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;

    public AgentExecutionException(ErrorCode errorCode, boolean retryable,
                                   String message) {
        this(errorCode, retryable, message, null);
    }

    public AgentExecutionException(ErrorCode errorCode, boolean retryable,
                                   String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
