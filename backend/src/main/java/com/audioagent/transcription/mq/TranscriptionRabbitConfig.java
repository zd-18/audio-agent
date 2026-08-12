package com.audioagent.transcription.mq;

import com.audioagent.transcription.config.TranscriptionProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(
        name = "audio.transcription.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TranscriptionRabbitConfig {

    @Bean
    public DirectExchange audioTranscriptionExchange() {
        return ExchangeBuilder.directExchange(
                TranscriptionRabbitConstants.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue audioTranscriptionTaskQueue() {
        return QueueBuilder.durable(TranscriptionRabbitConstants.TASK_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange",
                        TranscriptionRabbitConstants.DEAD_EXCHANGE,
                        "x-dead-letter-routing-key",
                        TranscriptionRabbitConstants.DEAD_ROUTING_KEY))
                .build();
    }

    @Bean
    public Binding audioTranscriptionTaskBinding() {
        return BindingBuilder.bind(audioTranscriptionTaskQueue())
                .to(audioTranscriptionExchange())
                .with(TranscriptionRabbitConstants.TASK_ROUTING_KEY);
    }

    @Bean
    public DirectExchange audioTranscriptionRetryExchange() {
        return ExchangeBuilder.directExchange(
                TranscriptionRabbitConstants.RETRY_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue audioTranscriptionRetryQueue(
            TranscriptionProperties properties) {
        return QueueBuilder.durable(TranscriptionRabbitConstants.RETRY_QUEUE)
                .withArguments(Map.of(
                        "x-message-ttl",
                        properties.getRetryDelayMilliseconds(),
                        "x-dead-letter-exchange",
                        TranscriptionRabbitConstants.EXCHANGE,
                        "x-dead-letter-routing-key",
                        TranscriptionRabbitConstants.TASK_ROUTING_KEY))
                .build();
    }

    @Bean
    public Binding audioTranscriptionRetryBinding(
            @Qualifier("audioTranscriptionRetryQueue")
            Queue audioTranscriptionRetryQueue) {
        return BindingBuilder.bind(audioTranscriptionRetryQueue)
                .to(audioTranscriptionRetryExchange())
                .with(TranscriptionRabbitConstants.RETRY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange audioTranscriptionDeadExchange() {
        return ExchangeBuilder.directExchange(
                TranscriptionRabbitConstants.DEAD_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue audioTranscriptionDeadQueue() {
        return QueueBuilder.durable(TranscriptionRabbitConstants.DEAD_QUEUE)
                .build();
    }

    @Bean
    public Binding audioTranscriptionDeadBinding() {
        return BindingBuilder.bind(audioTranscriptionDeadQueue())
                .to(audioTranscriptionDeadExchange())
                .with(TranscriptionRabbitConstants.DEAD_ROUTING_KEY);
    }
}
