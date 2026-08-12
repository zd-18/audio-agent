package com.audioagent.analysis.processing;

import java.util.List;

public record ProcessingPlanDraft(
        ProcessingPlanStatus status,
        String summary,
        Long estimatedOutputDurationMs,
        List<ProcessingStepDraft> steps,
        int mergedStepCount,
        int trimmedStepCount) {
}
