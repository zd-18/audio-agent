package com.audioagent.processing.exception;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessingExecutionErrorClassifier {

    public ProcessingExecutionException classify(Throwable error) {
        if (error instanceof ProcessingExecutionException executionError) {
            return executionError;
        }
        if (error instanceof DataAccessException) {
            return retryable(ErrorCode.PROCESSING_EXECUTION_FAILED,
                    "Database is temporarily unavailable", error);
        }
        if (error instanceof IOException) {
            return retryable(ErrorCode.PROCESSING_EXECUTION_FAILED,
                    "Temporary file operation failed", error);
        }
        if (error instanceof BusinessException business) {
            if (business.getCode()
                    == ErrorCode.MINIO_DOWNLOAD_FAILED.getCode()) {
                return retryable(
                        ErrorCode.PROCESSING_EXECUTION_FAILED,
                        "Source audio download failed temporarily", error);
            }
            if (business.getCode() == ErrorCode.MINIO_UPLOAD_FAILED.getCode()
                    || business.getCode()
                    == ErrorCode.MINIO_OBJECT_CHECK_FAILED.getCode()) {
                return retryable(
                        ErrorCode.PROCESSING_EXECUTION_UPLOAD_FAILED,
                        "Object storage is temporarily unavailable", error);
            }
            if (business.getCode()
                    == ErrorCode.MINIO_OBJECT_NOT_FOUND.getCode()) {
                return permanent(
                        ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                        "Source audio object does not exist", error);
            }
        }
        return permanent(ErrorCode.PROCESSING_EXECUTION_FAILED,
                "Audio processing execution failed", error);
    }

    private ProcessingExecutionException retryable(
            ErrorCode code, String message, Throwable error) {
        return new ProcessingExecutionException(code, true, message, error);
    }

    private ProcessingExecutionException permanent(
            ErrorCode code, String message, Throwable error) {
        return new ProcessingExecutionException(code, false, message, error);
    }
}
