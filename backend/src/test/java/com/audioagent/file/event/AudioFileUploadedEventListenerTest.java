package com.audioagent.file.event;

import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.worker.RabbitOutboxMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioFileUploadedEventListenerTest {

    private AudioAnalysisTaskService taskService;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private OutboxProperties properties;
    private AudioFileUploadedEventListener listener;

    @BeforeEach
    void setUp() {
        taskService = mock(AudioAnalysisTaskService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        properties = new OutboxProperties();
        properties.setConfirmTimeoutMs(1);
        listener = new AudioFileUploadedEventListener(
                new ObjectMapper(), taskService, rabbitTemplate, properties);
    }

    @Test
    void duplicateMqDeliveryUsesSameIdempotencyKeyAndAcknowledgesBoth()
            throws Exception {
        Message message = validMessage(0);

        listener.handle(message, channel);
        listener.handle(message, channel);

        verify(taskService, times(2)).createTaskFromUploadedFile(
                19L, 7L, 501L);
        verify(channel, times(2)).basicAck(88L, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void retryableFailurePublishesDelayedRetryThenAcknowledgesOriginal()
            throws Exception {
        when(taskService.createTaskFromUploadedFile(19L, 7L, 501L))
                .thenThrow(new IllegalStateException("temporary database"));
        confirmNextPublish(true);
        ArgumentCaptor<Message> outbound =
                ArgumentCaptor.forClass(Message.class);

        listener.handle(validMessage(0), channel);

        verify(rabbitTemplate).send(eq("audio-agent.events"),
                eq("outbox.event.retry"), outbound.capture(),
                any(CorrelationData.class));
        assertEquals(1, ((Number) outbound.getValue()
                .getMessageProperties().getHeader(
                        AudioFileUploadedEventListener.HEADER_RETRY_COUNT))
                .intValue());
        assertStableEventId(outbound.getValue());
        verify(channel).basicAck(88L, false);
        verify(channel, never()).basicNack(88L, false, true);
    }

    @Test
    void retryLimitPublishesDlqWithoutAnotherRetry() throws Exception {
        when(taskService.createTaskFromUploadedFile(19L, 7L, 501L))
                .thenThrow(new IllegalStateException("still unavailable"));
        confirmNextPublish(true);
        ArgumentCaptor<Message> outbound =
                ArgumentCaptor.forClass(Message.class);

        listener.handle(validMessage(3), channel);

        verify(rabbitTemplate).send(eq("audio-agent.events"),
                eq("outbox.event.dlq"), outbound.capture(),
                any(CorrelationData.class));
        assertEquals(3, ((Number) outbound.getValue()
                .getMessageProperties().getHeader(
                        AudioFileUploadedEventListener.HEADER_RETRY_COUNT))
                .intValue());
        assertStableEventId(outbound.getValue());
        verify(rabbitTemplate, never()).send(anyString(),
                eq("outbox.event.retry"), any(Message.class),
                any(CorrelationData.class));
        verify(channel).basicAck(88L, false);
    }

    @Test
    void invalidJsonPublishesDirectlyToDlq() throws Exception {
        confirmNextPublish(true);
        Message invalid = message("not-json", 0);

        listener.handle(invalid, channel);

        verify(rabbitTemplate).send(eq("audio-agent.events"),
                eq("outbox.event.dlq"), any(Message.class),
                any(CorrelationData.class));
        verify(taskService, never()).createTaskFromUploadedFile(
                any(), any(), any());
        verify(channel).basicAck(88L, false);
    }

    @Test
    void businessFailurePublishesDirectlyToDlq() throws Exception {
        when(taskService.createTaskFromUploadedFile(19L, 7L, 501L))
                .thenThrow(new BusinessException(
                        ErrorCode.AUDIO_TASK_STATUS_INVALID));
        confirmNextPublish(true);

        listener.handle(validMessage(0), channel);

        verify(rabbitTemplate).send(eq("audio-agent.events"),
                eq("outbox.event.dlq"), any(Message.class),
                any(CorrelationData.class));
        verify(channel).basicAck(88L, false);
    }

    @Test
    void retryPublishNackPreservesOriginalDelivery() throws Exception {
        retryableFailure();
        confirmNextPublish(false);

        listener.handle(validMessage(0), channel);

        verify(channel, never()).basicAck(88L, false);
        verify(channel).basicNack(88L, false, true);
    }

    @Test
    void retryPublishTimeoutPreservesOriginalDelivery() throws Exception {
        retryableFailure();

        listener.handle(validMessage(0), channel);

        verify(channel, never()).basicAck(88L, false);
        verify(channel).basicNack(88L, false, true);
    }

    @Test
    void dlqPublishNackPreservesOriginalDelivery() throws Exception {
        confirmNextPublish(false);

        listener.handle(message("not-json", 0), channel);

        verify(channel, never()).basicAck(88L, false);
        verify(channel).basicNack(88L, false, true);
    }

    @Test
    void dlqPublishTimeoutPreservesOriginalDelivery() throws Exception {
        listener.handle(message("not-json", 0), channel);

        verify(channel, never()).basicAck(88L, false);
        verify(channel).basicNack(88L, false, true);
    }

    private void retryableFailure() {
        when(taskService.createTaskFromUploadedFile(19L, 7L, 501L))
                .thenThrow(new IllegalStateException("temporary database"));
    }

    private void confirmNextPublish(boolean acknowledged) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(acknowledged,
                            acknowledged ? null : "broker nack"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(),
                any(Message.class), any(CorrelationData.class));
    }

    private Message validMessage(int retryCount) {
        return message("{\"audioFileId\":19,\"userId\":7,"
                + "\"eventVersion\":1}", retryCount);
    }

    private Message message(String body, int retryCount) {
        return MessageBuilder.withBody(
                        body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId("501")
                .setDeliveryTag(88L)
                .setHeader(RabbitOutboxMessageSender.HEADER_EVENT_ID, "501")
                .setHeader(RabbitOutboxMessageSender.HEADER_EVENT_TYPE,
                        AudioFileUploadedEventType.AUDIO_FILE_UPLOADED)
                .setHeader(AudioFileUploadedEventListener.HEADER_RETRY_COUNT,
                        retryCount)
                .build();
    }

    private void assertStableEventId(Message message) {
        assertEquals("501", message.getMessageProperties().getMessageId());
        assertEquals("501", message.getMessageProperties().getHeader(
                RabbitOutboxMessageSender.HEADER_EVENT_ID));
    }
}
