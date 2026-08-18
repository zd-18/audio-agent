package com.audioagent.outbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxConfigurationTest {

    @Test
    void declaresDurableMainRetryAndDeadLetterTopology() {
        OutboxProperties properties = new OutboxProperties();
        OutboxConfiguration configuration = new OutboxConfiguration();
        TopicExchange exchange = configuration.outboxExchange(properties);
        Queue main = configuration.outboxQueue(properties);
        Queue retry = configuration.outboxConsumerRetryQueue(properties);
        Queue dlq = configuration.outboxConsumerDlq(properties);
        Binding retryBinding = configuration.outboxConsumerRetryBinding(
                exchange, retry, properties);
        Binding dlqBinding = configuration.outboxConsumerDlqBinding(
                exchange, dlq, properties);

        assertTrue(main.isDurable());
        assertEquals("audio-agent.outbox.events", main.getName());
        assertTrue(retry.isDurable());
        assertEquals("audio-agent.outbox.events.retry", retry.getName());
        assertEquals(5_000, retry.getArguments().get("x-message-ttl"));
        assertEquals("audio-agent.events",
                retry.getArguments().get("x-dead-letter-exchange"));
        assertEquals("outbox.event", retry.getArguments()
                .get("x-dead-letter-routing-key"));
        assertEquals("outbox.event.retry", retryBinding.getRoutingKey());
        assertTrue(dlq.isDurable());
        assertEquals("audio-agent.outbox.events.dlq", dlq.getName());
        assertEquals("outbox.event.dlq", dlqBinding.getRoutingKey());
    }
}
