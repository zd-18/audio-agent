package com.audioagent.processing.exception;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingExecutionErrorClassifierTest {

    private final ProcessingExecutionErrorClassifier classifier =
            new ProcessingExecutionErrorClassifier();

    @Test
    void temporaryMinioDownloadFailureIsRetryable() {
        ProcessingExecutionException result = classifier.classify(
                new BusinessException(ErrorCode.MINIO_DOWNLOAD_FAILED));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_FAILED.name(),
                result.getFailureCode());
        assertTrue(result.isRetryable());
    }

    @Test
    void missingMinioObjectIsPermanent() {
        ProcessingExecutionException result = classifier.classify(
                new BusinessException(ErrorCode.MINIO_OBJECT_NOT_FOUND));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND
                .name(), result.getFailureCode());
        assertFalse(result.isRetryable());
    }
}
