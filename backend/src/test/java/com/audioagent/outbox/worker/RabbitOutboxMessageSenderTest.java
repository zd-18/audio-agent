package com.audioagent.outbox.worker;

import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitOutboxMessageSenderTest {

    @Test
    void usesStableEventIdAndCompletesAfterBrokerAck() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        OutboxProperties properties = new OutboxProperties();
        RabbitOutboxMessageSender sender =
                new RabbitOutboxMessageSender(template, properties);
        ArgumentCaptor<Message> messageCaptor =
                ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor =
                ArgumentCaptor.forClass(CorrelationData.class);

        OutboxEvent event = event();
        CompletableFuture<OutboxBrokerConfirmation> result =
                sender.send(event);

        verify(template).send(
                eq("audio-agent.events"), eq("outbox.event"),
                messageCaptor.capture(), correlationCaptor.capture());
        Message message = messageCaptor.getValue();
        CorrelationData correlation = correlationCaptor.getValue();
        assertEquals("42", message.getMessageProperties().getMessageId());
        assertEquals("42", correlation.getId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getDeliveryMode());
        assertEquals("{\"answer\":42}",
                new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals("sample.created", message.getMessageProperties()
                .getHeader(RabbitOutboxMessageSender.HEADER_EVENT_TYPE));
        assertTrue(!result.isDone());

        correlation.getFuture().complete(
                new CorrelationData.Confirm(true, null));

        assertTrue(result.join().acknowledged());
    }

    @Test
    void mapsBrokerNackToFailedConfirmation() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        OutboxProperties properties = new OutboxProperties();
        RabbitOutboxMessageSender sender =
                new RabbitOutboxMessageSender(template, properties);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(false, "exchange missing"));
            return null;
        }).when(template).send(
                eq("audio-agent.events"), eq("outbox.event"),
                org.mockito.ArgumentMatchers.any(Message.class),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));

        OutboxBrokerConfirmation result = sender.send(event()).join();

        assertTrue(!result.acknowledged());
        assertEquals("exchange missing", result.reason());
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(42L);
        event.setAggregateType("Sample");
        event.setAggregateId("7");
        event.setEventType("sample.created");
        event.setPayload("{\"answer\":42}");
        return event;
    }
}
