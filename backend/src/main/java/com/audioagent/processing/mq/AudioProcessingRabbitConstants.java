package com.audioagent.processing.mq;

public final class AudioProcessingRabbitConstants {

    private AudioProcessingRabbitConstants() {
    }

    public static final String EXCHANGE = "audio.processing.exchange";
    public static final String QUEUE = "audio.processing.queue";
    public static final String RETRY_QUEUE = "audio.processing.retry.queue";
    public static final String DEAD_LETTER_QUEUE = "audio.processing.dlq";
    public static final String EXECUTE_ROUTING_KEY =
            "audio.processing.execute";
    public static final String RETRY_ROUTING_KEY =
            "audio.processing.retry";
    public static final String DEAD_LETTER_ROUTING_KEY =
            "audio.processing.dead";
}
