package com.audioagent.analysis.mq;

/**
 * RabbitMQ 交换机、队列和路由键常量。
 * 集中管理，避免散落在多个类中。
 */
public final class AudioAnalysisRabbitConstants {

    private AudioAnalysisRabbitConstants() {
    }

    /** 主交换机／队列／路由键 */
    public static final String EXCHANGE = "audio.analysis.exchange";
    public static final String TASK_QUEUE = "audio.analysis.task.queue";
    public static final String TASK_ROUTING_KEY = "audio.analysis.task";

    /** 重试交换机／队列／路由键（TTL 延迟后投递回主交换机） */
    public static final String RETRY_EXCHANGE = "audio.analysis.retry.exchange";
    public static final String RETRY_QUEUE = "audio.analysis.retry.queue";
    public static final String RETRY_ROUTING_KEY = "audio.analysis.retry";

    /** 死信交换机／队列／路由键 */
    public static final String DEAD_EXCHANGE = "audio.analysis.dlx";
    public static final String DEAD_QUEUE = "audio.analysis.dead.queue";
    public static final String DEAD_ROUTING_KEY = "audio.analysis.dead";
}
