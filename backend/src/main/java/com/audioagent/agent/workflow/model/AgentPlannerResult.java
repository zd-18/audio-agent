package com.audioagent.agent.workflow.model;

import com.audioagent.analysis.processing.ProcessingPlanDraft;

public record AgentPlannerResult(
        ProcessingPlanDraft plan,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
