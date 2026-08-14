package com.audioagent.file.event;

import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.outbox.worker.RabbitOutboxMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AudioFileUploadedEventListenerTest {

    @Test
    void duplicateMqDeliveryUsesSameIdempotencyKeyAndAcknowledgesBoth()
            throws Exception {
        AudioAnalysisTaskService taskService =
                mock(AudioAnalysisTaskService.class);
        Channel channel = mock(Channel.class);
        AudioFileUploadedEventListener listener =
                new AudioFileUploadedEventListener(
                        new ObjectMapper(), taskService);
        Message message = MessageBuilder.withBody(("{"
                        + "\"audioFileId\":19,"
                        + "\"userId\":7,"
                        + "\"eventVersion\":1}")
                        .getBytes(StandardCharsets.UTF_8))
                .setDeliveryTag(88L)
                .setHeader(RabbitOutboxMessageSender.HEADER_EVENT_ID, "501")
                .setHeader(RabbitOutboxMessageSender.HEADER_EVENT_TYPE,
                        AudioFileUploadedEventType.AUDIO_FILE_UPLOADED)
                .build();

        listener.handle(message, channel);
        listener.handle(message, channel);

        verify(taskService, times(2)).createTaskFromUploadedFile(
                19L, 7L, 501L);
        verify(channel, times(2)).basicAck(88L, false);
    }
}
