package com.audioagent.contentanalysis.exception;

import com.audioagent.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class ContentAnalysisException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean retryable;
    private final Long retryAfterMilliseconds;

    public ContentAnalysisException(ErrorCode errorCode,
                                    boolean retryable,
                                    String message) {
        this(errorCode, retryable, message, null, null);
    }

    public ContentAnalysisException(ErrorCode errorCode,
                                    boolean retryable,
                                    String message,
                                    Throwable cause) {
        this(errorCode, retryable, message, null, cause);
    }

    public ContentAnalysisException(ErrorCode errorCode,
                                    boolean retryable,
                                    String message,
                                    Long retryAfterMilliseconds,
                                    Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfterMilliseconds = retryAfterMilliseconds;
    }
}
