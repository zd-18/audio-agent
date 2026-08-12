package com.audioagent.processing.snapshot;

import java.util.List;
import java.util.Map;

public record ProcessingExecutionSnapshot(
        Long confirmationId,
        Long taskId,
        Long audioFileId,
        Long planId,
        Integer sourcePlanRevision,
        List<Step> acceptedSteps
) {
    public record Step(
            Long stepConfirmationId,
            Long sourceStepId,
            Integer stepOrder,
            String operationType,
            Boolean userConfirmed,
            Long startMs,
            Long endMs,
            Map<String, Object> effectiveParameters
    ) {
    }
}
