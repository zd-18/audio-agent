package com.audioagent.analysis.mq;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.executor.AudioAnalysisTaskExecutor;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.rabbitmq.client.Channel;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AudioAnalysisTaskMessageListenerTest {

    private static final Long TASK_ID = 11L;
    private static final Long AUDIO_FILE_ID = 22L;
    private static final String MESSAGE_ID = "message-1";

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, AudioAnalysisTask.class);
    }

    private AudioAnalysisTaskExecutor executor;
    private AudioAnalysisTaskMapper taskMapper;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private AudioAnalysisTaskMessageListener listener;

    @BeforeEach
    void setUp() {
        executor = mock(AudioAnalysisTaskExecutor.class);
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        AnalysisProperties properties = new AnalysisProperties();
        properties.setProcessingLeaseSeconds(60);
        listener = new AudioAnalysisTaskMessageListener(
                executor, taskMapper, rabbitTemplate, properties,
                mock(TransactionTemplate.class));
    }

    @Test
    void successfulTaskDuplicateIsAckedWithoutExecutionOrDeadLetter()
            throws Exception {
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task(AnalysisTaskStatus.SUCCESS));

        for (int index = 0; index < 10; index++) {
            listener.handleMessage(taskMessage(), delivery(), channel);
        }

        verify(channel, times(10)).basicAck(71L, false);
        verifyNoInteractions(executor, rabbitTemplate);
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void redeliveryAfterSuccessCommitAndAckFailureStaysSuccessful()
            throws Exception {
        when(taskMapper.selectById(TASK_ID)).thenReturn(
                task(AnalysisTaskStatus.PENDING),
                task(AnalysisTaskStatus.SUCCESS));
        doThrow(new IOException("channel closed"))
                .doNothing()
                .when(channel).basicAck(71L, false);

        listener.handleMessage(taskMessage(), delivery(), channel);
        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(executor, times(1)).execute(
                anyLong(), anyLong(), anyString());
        verify(channel, times(2)).basicAck(71L, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void activeProcessingDuplicateIsAckedWithoutFailureOrDeadLetter()
            throws Exception {
        AudioAnalysisTask processing = task(AnalysisTaskStatus.PROCESSING);
        processing.setLastMessageId("another-message|owner");
        processing.setUpdatedAt(LocalDateTime.now());
        when(taskMapper.selectById(TASK_ID)).thenReturn(processing);

        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(channel).basicAck(71L, false);
        verifyNoInteractions(executor, rabbitTemplate);
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void activeRedeliveryFromCurrentMessageIsDeferredWithoutTaskFailure()
            throws Exception {
        AudioAnalysisTask processing = task(AnalysisTaskStatus.PROCESSING);
        processing.setLastMessageId(MESSAGE_ID + "|owner");
        processing.setUpdatedAt(LocalDateTime.now());
        when(taskMapper.selectById(TASK_ID)).thenReturn(processing);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(
                        AudioAnalysisRabbitConstants.RETRY_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(
                        AudioAnalysisRabbitConstants.RETRY_ROUTING_KEY),
                org.mockito.ArgumentMatchers.eq(taskMessage()),
                any(CorrelationData.class));

        AudioAnalysisTaskMessage taskMessage = taskMessage();
        listener.handleMessage(taskMessage, delivery(), channel);

        verify(channel).basicAck(71L, false);
        verifyNoInteractions(executor);
        verify(taskMapper, never()).update(any(), any());
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(
                        AudioAnalysisRabbitConstants.RETRY_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(
                        AudioAnalysisRabbitConstants.RETRY_ROUTING_KEY),
                org.mockito.ArgumentMatchers.eq(taskMessage),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
    }

    @Test
    void pendingClaimLoserDoesNotFailTaskOrPublishDeadLetter()
            throws Exception {
        AudioAnalysisTask pending = task(AnalysisTaskStatus.PENDING);
        AudioAnalysisTask processing = task(AnalysisTaskStatus.PROCESSING);
        processing.setLastMessageId("message-1|winner");
        processing.setUpdatedAt(LocalDateTime.now());
        when(taskMapper.selectById(TASK_ID)).thenReturn(
                pending, pending, processing);
        doNothing().doThrow(new AudioAnalysisException(
                        AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED,
                        false, "claim lost"))
                .when(executor).execute(anyLong(), anyLong(), anyString());

        listener.handleMessage(taskMessage(), delivery(), channel);
        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(executor, times(2)).execute(
                anyLong(), anyLong(), anyString());
        verify(channel, times(2)).basicAck(71L, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void failedTaskOldMessageIsAckedAndManualRetryRemainsRequired()
            throws Exception {
        when(taskMapper.selectById(TASK_ID))
                .thenReturn(task(AnalysisTaskStatus.FAILED));

        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(channel).basicAck(71L, false);
        verifyNoInteractions(executor, rabbitTemplate);
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void freshProcessingLeaseCannotBeTakenOver() throws Exception {
        AudioAnalysisTask processing = task(AnalysisTaskStatus.PROCESSING);
        processing.setLastMessageId("another-message|owner");
        processing.setUpdatedAt(LocalDateTime.now().minusSeconds(30));
        when(taskMapper.selectById(TASK_ID)).thenReturn(processing);

        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(taskMapper, never()).update(any(), any());
        verify(executor, never()).executeClaimed(
                anyLong(), anyLong(), anyString());
        verify(channel).basicAck(71L, false);
    }

    @Test
    void onlyOneDeliveryCanRecoverTheSameStaleProcessingLease()
            throws Exception {
        AudioAnalysisTask stale = task(AnalysisTaskStatus.PROCESSING);
        stale.setLastMessageId("old-message|old-owner");
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        AudioAnalysisTask recovered = task(AnalysisTaskStatus.PROCESSING);
        recovered.setLastMessageId("message-1|new-owner");
        recovered.setUpdatedAt(LocalDateTime.now());
        when(taskMapper.selectById(TASK_ID)).thenReturn(
                stale, stale, recovered);
        when(taskMapper.update(any(), any())).thenReturn(1, 0);

        listener.handleMessage(taskMessage(), delivery(), channel);
        listener.handleMessage(taskMessage(), delivery(), channel);

        verify(executor, times(1)).executeClaimed(
                anyLong(), anyLong(), anyString());
        verify(channel, times(2)).basicAck(71L, false);
        verifyNoInteractions(rabbitTemplate);
    }

    private AudioAnalysisTask task(AnalysisTaskStatus status) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(TASK_ID);
        task.setAudioFileId(AUDIO_FILE_ID);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private AudioAnalysisTaskMessage taskMessage() {
        return AudioAnalysisTaskMessage.builder()
                .taskId(TASK_ID)
                .messageId(MESSAGE_ID)
                .originalMessageId(MESSAGE_ID)
                .retryCount(0)
                .build();
    }

    private Message delivery() {
        return MessageBuilder.withBody(new byte[0])
                .setDeliveryTag(71L)
                .build();
    }
}
