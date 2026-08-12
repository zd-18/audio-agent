package com.audioagent.processing.mq;

import com.audioagent.processing.config.AudioProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.processing.enabled",
        havingValue = "true", matchIfMissing = true)
public class AudioProcessingRabbitConfig {

    private final AudioProcessingProperties properties;

    @Bean
    public DirectExchange audioProcessingExchange() {
        return ExchangeBuilder.directExchange(
                AudioProcessingRabbitConstants.EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue audioProcessingQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange",
                AudioProcessingRabbitConstants.EXCHANGE);
        arguments.put("x-dead-letter-routing-key",
                AudioProcessingRabbitConstants.DEAD_LETTER_ROUTING_KEY);
        return QueueBuilder.durable(AudioProcessingRabbitConstants.QUEUE)
                .withArguments(arguments).build();
    }

    @Bean
    public Binding audioProcessingBinding() {
        return BindingBuilder.bind(audioProcessingQueue())
                .to(audioProcessingExchange())
                .with(AudioProcessingRabbitConstants.EXECUTE_ROUTING_KEY);
    }

    @Bean
    public Queue audioProcessingRetryQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl",
                properties.getRetryDelayMilliseconds());
        arguments.put("x-dead-letter-exchange",
                AudioProcessingRabbitConstants.EXCHANGE);
        arguments.put("x-dead-letter-routing-key",
                AudioProcessingRabbitConstants.EXECUTE_ROUTING_KEY);
        return QueueBuilder.durable(
                AudioProcessingRabbitConstants.RETRY_QUEUE)
                .withArguments(arguments).build();
    }

    @Bean
    public Binding audioProcessingRetryBinding() {
        return BindingBuilder.bind(audioProcessingRetryQueue())
                .to(audioProcessingExchange())
                .with(AudioProcessingRabbitConstants.RETRY_ROUTING_KEY);
    }

    @Bean
    public Queue audioProcessingDeadLetterQueue() {
        return QueueBuilder.durable(
                AudioProcessingRabbitConstants.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding audioProcessingDeadLetterBinding() {
        return BindingBuilder.bind(audioProcessingDeadLetterQueue())
                .to(audioProcessingExchange())
                .with(AudioProcessingRabbitConstants
                        .DEAD_LETTER_ROUTING_KEY);
    }
}
