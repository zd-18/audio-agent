package com.audioagent.transcription.mq;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.exception.TranscriptionErrorClassifier;
import com.audioagent.transcription.exception.TranscriptionException;
import com.audioagent.transcription.executor.AudioTranscriptionTaskExecutor;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLSyntaxErrorException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionTaskMessageListenerTest {

    @Mock AudioTranscriptionTaskExecutor executor;
    @Mock AudioTranscriptionTaskMapper taskMapper;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock Channel channel;
    private TranscriptionTaskMessageListener listener;
    private Message message;

    @BeforeEach
    void setUp() {
        TranscriptionProperties properties = new TranscriptionProperties();
        properties.setMaxRetryCount(3);
        listener = new TranscriptionTaskMessageListener(executor,
                taskMapper, rabbitTemplate, properties,
                new TranscriptionErrorClassifier());
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(99L);
        message = new Message(new byte[0], messageProperties);
    }

    @Test
    void successfulConsumerAcknowledgesMessage() throws Exception {
        when(taskMapper.claim(eq(90L), any())).thenReturn(1);
        listener.handle(TranscriptionTaskMessage.first(90L),
                message, channel);
        verify(executor).execute(90L);
        verify(channel).basicAck(99L, false);
    }

    @Test
    void duplicateConsumerDoesNotTranscribeAgain() throws Exception {
        when(taskMapper.claim(eq(90L), any())).thenReturn(0);
        listener.handle(TranscriptionTaskMessage.first(90L),
                message, channel);
        verify(executor, never()).execute(any());
        verify(channel).basicAck(99L, false);
    }

    @Test
    void retryableFailureGoesToDelayQueue() throws Exception {
        when(taskMapper.claim(eq(90L), any())).thenReturn(1);
        when(taskMapper.scheduleRetry(eq(90L), eq(1),
                eq("ASR_SERVICE_UNAVAILABLE"), anyString(), any()))
                .thenReturn(1);
        org.mockito.Mockito.doThrow(new TranscriptionException(
                ErrorCode.ASR_SERVICE_UNAVAILABLE, true,
                "语音识别服务暂时不可用"))
                .when(executor).execute(90L);

        listener.handle(TranscriptionTaskMessage.first(90L),
                message, channel);

        verify(rabbitTemplate).convertAndSend(
                eq(TranscriptionRabbitConstants.RETRY_EXCHANGE),
                eq(TranscriptionRabbitConstants.RETRY_ROUTING_KEY),
                any(TranscriptionTaskMessage.class),
                any(CorrelationData.class));
        verify(channel).basicAck(99L, false);
    }

    @Test
    void nonRetryableFailureIsPersistedAndDeadLettered()
            throws Exception {
        when(taskMapper.claim(eq(90L), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new TranscriptionException(
                ErrorCode.ASR_RESPONSE_INVALID, false,
                "语音识别结果格式不正确"))
                .when(executor).execute(90L);

        listener.handle(TranscriptionTaskMessage.first(90L),
                message, channel);

        verify(taskMapper).markFailed(eq(90L),
                eq("ASR_RESPONSE_INVALID"), anyString(), any());
        verify(rabbitTemplate).convertAndSend(
                eq(TranscriptionRabbitConstants.DEAD_EXCHANGE),
                eq(TranscriptionRabbitConstants.DEAD_ROUTING_KEY),
                any(TranscriptionTaskMessage.class),
                any(CorrelationData.class));
        verify(channel).basicAck(99L, false);
    }

    @Test
    void schemaFailureNeverEntersDelayedRetryQueue() throws Exception {
        when(taskMapper.claim(eq(90L), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new BadSqlGrammarException(
                        "insert segment",
                        "INSERT INTO audio_transcript_segment(user_id) "
                                + "VALUES (?)",
                        new SQLSyntaxErrorException(
                                "Unknown column 'user_id'")))
                .when(executor).execute(90L);

        listener.handle(TranscriptionTaskMessage.first(90L),
                message, channel);

        verify(taskMapper, never()).scheduleRetry(
                any(), anyInt(), anyString(),
                anyString(), any());
        verify(taskMapper).markFailed(eq(90L),
                eq("TRANSCRIPT_PERSISTENCE_FAILED"),
                eq("文字稿保存失败，请联系管理员"), any());
        verify(rabbitTemplate, never()).convertAndSend(
                eq(TranscriptionRabbitConstants.RETRY_EXCHANGE),
                eq(TranscriptionRabbitConstants.RETRY_ROUTING_KEY),
                any(TranscriptionTaskMessage.class),
                any(CorrelationData.class));
        verify(channel).basicAck(99L, false);
    }
}
