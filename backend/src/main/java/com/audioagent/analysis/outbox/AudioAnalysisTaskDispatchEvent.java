package com.audioagent.analysis.outbox;

public final class AudioAnalysisTaskDispatchEvent {

    public static final String AGGREGATE_TYPE = "AUDIO_ANALYSIS_TASK";
    public static final String EVENT_TYPE = "AUDIO_ANALYSIS_TASK_CREATED";

    private AudioAnalysisTaskDispatchEvent() {
    }

    public record Payload(Long taskId) {
    }
}
