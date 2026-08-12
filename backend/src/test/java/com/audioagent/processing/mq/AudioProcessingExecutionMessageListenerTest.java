package com.audioagent.processing.mq;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.exception.ProcessingExecutionErrorClassifier;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.executor.AudioProcessingExecutionExecutor;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingExecutionMessageListenerTest {

    private AudioProcessingExecutionExecutor executor;
    private AudioProcessingExecutionMapper executionMapper;
    private AudioProcessingExecutionStepMapper stepMapper;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private AudioProcessingExecutionMessageListener listener;

    @BeforeEach
    void setUp() {
        executor = mock(AudioProcessingExecutionExecutor.class);
        executionMapper = mock(AudioProcessingExecutionMapper.class);
        stepMapper = mock(AudioProcessingExecutionStepMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        TransactionTemplate transactionTemplate =
                mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        listener = new AudioProcessingExecutionMessageListener(executor,
                executionMapper, stepMapper,
                new ProcessingExecutionErrorClassifier(), rabbitTemplate,
                transactionTemplate);
    }

    @Test
    void successMessageIsAcknowledged() throws Exception {
        listener.handle(new AudioProcessingExecutionMessage(90L),
                message(), channel);
        verify(executor).execute(90L);
        verify(channel).basicAck(1L, false);
    }

    @Test
    void nonRetryableFailureBecomesFailedWithoutRetryPublish()
            throws Exception {
        ProcessingExecutionException error = new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                false, "Invalid parameters");
        doThrow(error).when(executor).execute(90L);
        when(executionMapper.selectExecutionById(90L))
                .thenReturn(processing(0, 3));

        listener.handle(new AudioProcessingExecutionMessage(90L),
                message(), channel);

        verify(executionMapper).markFailed(eq(90L), eq("FAILED"),
                eq(error.getFailureCode()), anyString(), any());
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), eq(AudioProcessingRabbitConstants
                        .RETRY_ROUTING_KEY), any(Object.class));
        verify(channel).basicAck(1L, false);
    }

    @Test
    void retryableFailureUsesDelayQueue() throws Exception {
        ProcessingExecutionException error = new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                true, "FFmpeg timed out");
        doThrow(error).when(executor).execute(90L);
        when(executionMapper.selectExecutionById(90L))
                .thenReturn(processing(0, 3));
        when(executionMapper.scheduleRetry(eq(90L), eq(1),
                anyString(), anyString(), any())).thenReturn(1);
        ArgumentCaptor<AudioProcessingExecutionMessage> payload =
                ArgumentCaptor.forClass(
                        AudioProcessingExecutionMessage.class);

        listener.handle(new AudioProcessingExecutionMessage(90L),
                message(), channel);

        verify(rabbitTemplate).convertAndSend(
                eq(AudioProcessingRabbitConstants.EXCHANGE),
                eq(AudioProcessingRabbitConstants.RETRY_ROUTING_KEY),
                payload.capture());
        assertEquals(90L, payload.getValue().getExecutionId());
        verify(stepMapper).resetForRetry(eq(90L), any());
    }

    @Test
    void maxRetryFailureBecomesDeadLetter() throws Exception {
        ProcessingExecutionException error = new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                true, "FFmpeg timed out");
        doThrow(error).when(executor).execute(90L);
        when(executionMapper.selectExecutionById(90L))
                .thenReturn(processing(3, 3));

        listener.handle(new AudioProcessingExecutionMessage(90L),
                message(), channel);

        verify(executionMapper).markFailed(eq(90L), eq("DEAD_LETTER"),
                anyString(), anyString(), any());
        verify(rabbitTemplate).convertAndSend(
                AudioProcessingRabbitConstants.EXCHANGE,
                AudioProcessingRabbitConstants.DEAD_LETTER_ROUTING_KEY,
                new AudioProcessingExecutionMessage(90L));
    }

    private AudioProcessingExecution processing(int retry, int maxRetry) {
        AudioProcessingExecution execution = new AudioProcessingExecution();
        execution.setId(90L);
        execution.setExecutionStatus("PROCESSING");
        execution.setRetryCount(retry);
        execution.setMaxRetryCount(maxRetry);
        return execution;
    }

    private Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        return new Message(new byte[0], properties);
    }
}
