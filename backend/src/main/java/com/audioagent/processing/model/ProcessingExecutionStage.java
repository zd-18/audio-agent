package com.audioagent.processing.model;

public enum ProcessingExecutionStage {
    PREPARING,
    LOCAL_PROCESSING,
    TRIMMING,
    DENOISING,
    LOUDNESS_NORMALIZING,
    PEAK_LIMITING,
    REVIEWING,
    UPLOADING,
    METADATA_EXTRACTING,
    COMPLETED
}
