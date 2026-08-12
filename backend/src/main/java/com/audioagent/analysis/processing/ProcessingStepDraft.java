package com.audioagent.analysis.processing;

import java.util.Map;

public record ProcessingStepDraft(
        ProcessingOperationType operationType,
        String title,
        String description,
        Long sourceIssueId,
        Long startMs,
        Long endMs,
        ProcessingPriority priority,
        ProcessingRiskLevel riskLevel,
        boolean requiresConfirmation,
        Map<String, Object> parameters,
        String reason) {
}
