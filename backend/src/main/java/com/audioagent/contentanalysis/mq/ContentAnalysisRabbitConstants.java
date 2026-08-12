package com.audioagent.contentanalysis.mq;

public final class ContentAnalysisRabbitConstants {

    private ContentAnalysisRabbitConstants() {
    }

    public static final String EXCHANGE =
            "audio.content-analysis.exchange";
    public static final String TASK_QUEUE =
            "audio.content-analysis.task.queue";
    public static final String TASK_ROUTING_KEY =
            "audio.content-analysis.task";

    public static final String RETRY_EXCHANGE =
            "audio.content-analysis.retry.exchange";
    public static final String RETRY_QUEUE =
            "audio.content-analysis.retry.queue";
    public static final String RETRY_ROUTING_KEY =
            "audio.content-analysis.retry";

    public static final String DEAD_EXCHANGE =
            "audio.content-analysis.dlx";
    public static final String DEAD_QUEUE =
            "audio.content-analysis.dead.queue";
    public static final String DEAD_ROUTING_KEY =
            "audio.content-analysis.dead";
}
