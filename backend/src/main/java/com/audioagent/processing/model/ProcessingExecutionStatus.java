package com.audioagent.processing.model;

public enum ProcessingExecutionStatus {
    PENDING,
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    DEAD_LETTER
}
