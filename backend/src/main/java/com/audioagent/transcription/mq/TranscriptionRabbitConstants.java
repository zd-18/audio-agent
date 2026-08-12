package com.audioagent.transcription.mq;

public final class TranscriptionRabbitConstants {

    private TranscriptionRabbitConstants() {
    }

    public static final String EXCHANGE = "audio.transcription.exchange";
    public static final String TASK_QUEUE = "audio.transcription.task.queue";
    public static final String TASK_ROUTING_KEY = "audio.transcription.task";

    public static final String RETRY_EXCHANGE =
            "audio.transcription.retry.exchange";
    public static final String RETRY_QUEUE =
            "audio.transcription.retry.queue";
    public static final String RETRY_ROUTING_KEY =
            "audio.transcription.retry";

    public static final String DEAD_EXCHANGE = "audio.transcription.dlx";
    public static final String DEAD_QUEUE = "audio.transcription.dead.queue";
    public static final String DEAD_ROUTING_KEY = "audio.transcription.dead";
}
