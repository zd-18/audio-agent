package com.audioagent.processing.exception;

import com.audioagent.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class ProcessingExecutionException extends RuntimeException {

    private final String failureCode;
    private final boolean retryable;

    public ProcessingExecutionException(ErrorCode errorCode,
                                        boolean retryable,
                                        String message) {
        super(message);
        this.failureCode = errorCode.name();
        this.retryable = retryable;
    }

    public ProcessingExecutionException(ErrorCode errorCode,
                                        boolean retryable,
                                        String message,
                                        Throwable cause) {
        super(message, cause);
        this.failureCode = errorCode.name();
        this.retryable = retryable;
    }
}
