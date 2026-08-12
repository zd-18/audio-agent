package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;

import java.util.Map;

public record ExecutableProcessingStep(
        Long executionStepId,
        Integer stepOrder,
        ProcessingOperationType operationType,
        Long startMs,
        Long endMs,
        Map<String, Object> parameters
) {
}
