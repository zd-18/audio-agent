package com.audioagent.outbox.worker;

import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.analysis.outbox.AudioAnalysisTaskDispatchEvent;
import com.audioagent.analysis.mq.AudioAnalysisTaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
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
                new RabbitOutboxMessageSender(
                        template, properties, new ObjectMapper());
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
                new RabbitOutboxMessageSender(
                        template, properties, new ObjectMapper());
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

    @Test
    void mapsReturnedMessageToFailedConfirmation() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitOutboxMessageSender sender =
                new RabbitOutboxMessageSender(template,
                        new OutboxProperties(), new ObjectMapper());
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            Message returnedMessage = invocation.getArgument(2);
            correlation.setReturned(new ReturnedMessage(
                    returnedMessage, 312, "NO_ROUTE",
                    "audio-agent.events", "outbox.event"));
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(
                eq("audio-agent.events"), eq("outbox.event"),
                org.mockito.ArgumentMatchers.any(Message.class),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));

        OutboxBrokerConfirmation result = sender.send(event()).join();

        assertTrue(!result.acknowledged());
        assertTrue(result.reason().contains("unroutable"));
    }

    @Test
    void analysisDispatchEventGoesDirectlyToFinalAnalysisDestination()
            throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RabbitOutboxMessageSender sender = new RabbitOutboxMessageSender(
                template, new OutboxProperties(), mapper);
        ArgumentCaptor<Message> messageCaptor =
                ArgumentCaptor.forClass(Message.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(
                eq("audio.analysis.exchange"), eq("audio.analysis.task"),
                org.mockito.ArgumentMatchers.any(Message.class),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
        OutboxEvent event = event();
        event.setId(700L);
        event.setAggregateType(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE);
        event.setAggregateId("31");
        event.setEventType(AudioAnalysisTaskDispatchEvent.EVENT_TYPE);
        event.setPayload("{\"taskId\":31}");

        OutboxBrokerConfirmation confirmation = sender.send(event).join();

        verify(template).send(eq("audio.analysis.exchange"),
                eq("audio.analysis.task"), messageCaptor.capture(),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
        Message sent = messageCaptor.getValue();
        AudioAnalysisTaskMessage body = mapper.readValue(
                sent.getBody(), AudioAnalysisTaskMessage.class);
        assertEquals(31L, body.getTaskId());
        assertEquals("700", body.getMessageId());
        assertEquals("700", body.getOriginalMessageId());
        assertEquals("700", sent.getMessageProperties().getMessageId());
        assertTrue(confirmation.acknowledged());
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
