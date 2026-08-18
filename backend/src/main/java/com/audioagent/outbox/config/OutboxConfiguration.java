package com.audioagent.outbox.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    TopicExchange outboxExchange(OutboxProperties properties) {
        return ExchangeBuilder.topicExchange(properties.getExchange())
                .durable(true)
                .build();
    }

    @Bean
    Queue outboxQueue(OutboxProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    Binding outboxBinding(
            TopicExchange outboxExchange,
            Queue outboxQueue,
            OutboxProperties properties) {
        return BindingBuilder.bind(outboxQueue)
                .to(outboxExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    Queue outboxConsumerRetryQueue(OutboxProperties properties) {
        return QueueBuilder.durable(properties.getConsumerRetryQueue())
                .ttl(properties.getConsumerRetryDelayMs())
                .deadLetterExchange(properties.getExchange())
                .deadLetterRoutingKey(properties.getRoutingKey())
                .build();
    }

    @Bean
    Binding outboxConsumerRetryBinding(
            TopicExchange outboxExchange,
            Queue outboxConsumerRetryQueue,
            OutboxProperties properties) {
        return BindingBuilder.bind(outboxConsumerRetryQueue)
                .to(outboxExchange)
                .with(properties.getConsumerRetryRoutingKey());
    }

    @Bean
    Queue outboxConsumerDlq(OutboxProperties properties) {
        return QueueBuilder.durable(properties.getConsumerDlq()).build();
    }

    @Bean
    Binding outboxConsumerDlqBinding(
            TopicExchange outboxExchange,
            Queue outboxConsumerDlq,
            OutboxProperties properties) {
        return BindingBuilder.bind(outboxConsumerDlq)
                .to(outboxExchange)
                .with(properties.getConsumerDlqRoutingKey());
    }
}
