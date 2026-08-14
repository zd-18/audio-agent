package com.audioagent.agent.workflow.model;

import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;

public record AgentWorkflowPlanningResult(
        AgentProcessingWorkflowVO workflow,
        String assistantMessage,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
