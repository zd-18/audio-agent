package com.audioagent.agent.workflow.model;

public record AgentProcessingContext(
        Long taskId,
        Long audioFileId,
        String audioFileName,
        Long durationMs,
        Integer sampleRate,
        Integer channels,
        String transcriptContext) {
}
