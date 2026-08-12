package com.audioagent.processing.model;

public enum ProcessingExecutionStage {
    PREPARING,
    LOCAL_PROCESSING,
    TRIMMING,
    LOUDNESS_NORMALIZING,
    PEAK_LIMITING,
    UPLOADING,
    METADATA_EXTRACTING,
    COMPLETED
}
