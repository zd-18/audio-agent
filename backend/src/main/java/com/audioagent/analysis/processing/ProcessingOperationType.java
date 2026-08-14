package com.audioagent.analysis.processing;

public enum ProcessingOperationType {
    NORMALIZE_VOLUME,
    TRIM_SEGMENT,

    // Legacy values remain readable for existing plans. New execution
    // snapshots accept only the two operations above.
    REVIEW_SILENCE,
    TRIM_SILENCE,
    INCREASE_GAIN,
    DECREASE_GAIN,
    DENOISE_REVIEW,
    NORMALIZE_LOUDNESS,
    LIMIT_PEAK;

    public boolean isExecutable() {
        return this == NORMALIZE_VOLUME || this == TRIM_SEGMENT;
    }
}
