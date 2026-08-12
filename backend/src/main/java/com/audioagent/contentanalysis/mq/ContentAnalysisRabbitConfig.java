package com.audioagent.contentanalysis.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(
        name = "audio-agent.ai.deepseek.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ContentAnalysisRabbitConfig {

    @Bean
    public DirectExchange contentAnalysisExchange() {
        return ExchangeBuilder.directExchange(
                ContentAnalysisRabbitConstants.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue contentAnalysisTaskQueue() {
        return QueueBuilder.durable(
                        ContentAnalysisRabbitConstants.TASK_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange",
                        ContentAnalysisRabbitConstants.DEAD_EXCHANGE,
                        "x-dead-letter-routing-key",
                        ContentAnalysisRabbitConstants.DEAD_ROUTING_KEY))
                .build();
    }

    @Bean
    public Binding contentAnalysisTaskBinding() {
        return BindingBuilder.bind(contentAnalysisTaskQueue())
                .to(contentAnalysisExchange())
                .with(ContentAnalysisRabbitConstants.TASK_ROUTING_KEY);
    }

    @Bean
    public DirectExchange contentAnalysisRetryExchange() {
        return ExchangeBuilder.directExchange(
                ContentAnalysisRabbitConstants.RETRY_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue contentAnalysisRetryQueue() {
        return QueueBuilder.durable(
                        ContentAnalysisRabbitConstants.RETRY_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange",
                        ContentAnalysisRabbitConstants.EXCHANGE,
                        "x-dead-letter-routing-key",
                        ContentAnalysisRabbitConstants.TASK_ROUTING_KEY))
                .build();
    }

    @Bean
    public Binding contentAnalysisRetryBinding(
            @Qualifier("contentAnalysisRetryQueue")
            Queue retryQueue) {
        return BindingBuilder.bind(retryQueue)
                .to(contentAnalysisRetryExchange())
                .with(ContentAnalysisRabbitConstants.RETRY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange contentAnalysisDeadExchange() {
        return ExchangeBuilder.directExchange(
                ContentAnalysisRabbitConstants.DEAD_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue contentAnalysisDeadQueue() {
        return QueueBuilder.durable(
                ContentAnalysisRabbitConstants.DEAD_QUEUE).build();
    }

    @Bean
    public Binding contentAnalysisDeadBinding() {
        return BindingBuilder.bind(contentAnalysisDeadQueue())
                .to(contentAnalysisDeadExchange())
                .with(ContentAnalysisRabbitConstants.DEAD_ROUTING_KEY);
    }
}
