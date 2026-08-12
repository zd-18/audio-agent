package com.audioagent.transcription.exception;

import com.audioagent.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class TranscriptionException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;

    public TranscriptionException(ErrorCode errorCode, boolean retryable,
                                  String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public TranscriptionException(ErrorCode errorCode, boolean retryable,
                                  String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
