package com.audioagent.contentanalysis.mq;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisErrorClassifier;
import com.audioagent.contentanalysis.executor.AudioContentAnalysisTaskExecutor;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.validation.AnalysisResponseDiagnostics;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationError;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationException;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationStage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContentAnalysisTaskMessageListenerTest {

    @Mock AudioContentAnalysisTaskExecutor executor;
    @Mock AudioContentAnalysisTaskMapper taskMapper;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock Channel channel;

    @Test
    void duplicateMessageDoesNotExecuteOrCreateAnotherResult()
            throws Exception {
        ContentAnalysisTaskMessageListener listener =
                new ContentAnalysisTaskMessageListener(
                        executor, taskMapper, rabbitTemplate,
                        new DeepSeekProperties(),
                        new ContentAnalysisErrorClassifier());
        when(taskMapper.claim(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(17L);
        Message message = new Message(new byte[0], properties);

        listener.handle(
                new ContentAnalysisTaskMessage(91L),
                message, channel);

        verify(executor, never()).execute(91L);
        verify(channel).basicAck(17L, false);
    }

    @Test
    void badSqlGrammarFailsTaskWithoutSchedulingAiRetry()
            throws Exception {
        ContentAnalysisTaskMessageListener listener =
                new ContentAnalysisTaskMessageListener(
                        executor, taskMapper, rabbitTemplate,
                        new DeepSeekProperties(),
                        new ContentAnalysisErrorClassifier());
        AudioContentAnalysisTask task =
                new AudioContentAnalysisTask();
        task.setId(91L);
        task.setStatus(ContentAnalysisTaskStatus.RUNNING);
        task.setRetryCount(0);
        when(taskMapper.claim(eq(91L), any())).thenReturn(1);
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(taskMapper.markFailed(
                eq(91L), eq(ErrorCode.DATABASE_SCHEMA_ERROR.name()),
                any(), any())).thenReturn(1);
        doThrow(new BadSqlGrammarException(
                "insert", "INSERT INTO result",
                new SQLException(
                        "Unknown column", "42S22", 1054)))
                .when(executor).execute(91L);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(18L);
        Message message = new Message(new byte[0], properties);

        listener.handle(
                new ContentAnalysisTaskMessage(91L),
                message, channel);

        verify(taskMapper, never()).scheduleRetry(
                eq(91L), anyInt(), anyString(), anyString(), any());
        verify(taskMapper).markFailed(
                eq(91L), eq(ErrorCode.DATABASE_SCHEMA_ERROR.name()),
                any(), any());
        verify(executor).execute(91L);
        verify(channel).basicAck(18L, false);
    }

    @Test
    void finalValidationFailureLogIncludesStageAndStructuredCodes(
            CapturedOutput output) throws Exception {
        ContentAnalysisTaskMessageListener listener =
                new ContentAnalysisTaskMessageListener(
                        executor, taskMapper, rabbitTemplate,
                        new DeepSeekProperties(),
                        new ContentAnalysisErrorClassifier());
        AudioContentAnalysisTask task =
                new AudioContentAnalysisTask();
        task.setId(91L);
        task.setTranscriptId(81L);
        task.setModelName("test-model");
        task.setPromptVersion("content-analysis-v1");
        task.setStatus(ContentAnalysisTaskStatus.RUNNING);
        task.setRetryCount(0);
        when(taskMapper.claim(eq(91L), any())).thenReturn(1);
        when(taskMapper.selectById(91L)).thenReturn(task);
        when(taskMapper.markFailed(
                eq(91L), eq(ErrorCode.AI_RESPONSE_INVALID.name()),
                any(), any())).thenReturn(1);
        String sensitiveResponse =
                "SENSITIVE_MODEL_RESPONSE_MUST_NOT_BE_LOGGED";
        AnalysisResultValidationException validation =
                new AnalysisResultValidationException(
                        AnalysisResultValidationStage.REPAIR_VALIDATION,
                        List.of(new AnalysisResultValidationError(
                                AnalysisResultValidationErrorCode
                                        .UNKNOWN_CHUNK_ID,
                                "章节引用了不存在的 chunkId",
                                "chapters[1].startChunkId")),
                        List.of("S1-C1", "S1-C2"),
                        AnalysisResponseDiagnostics.from(
                                sensitiveResponse, null),
                        null);
        doThrow(new ContentAnalysisException(
                ErrorCode.AI_RESPONSE_INVALID,
                false,
                "智能分析结果格式不正确",
                validation))
                .when(executor).execute(91L);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(19L);
        Message message = new Message(new byte[0], properties);

        listener.handle(
                new ContentAnalysisTaskMessage(91L),
                message,
                channel);

        assertTrue(output.getAll().contains(
                "stage=REPAIR_VALIDATION"));
        assertTrue(output.getAll().contains(
                "diagnosticCode=REPAIR_RESULT_VALIDATION_FAILED"));
        assertTrue(output.getAll().contains(
                "errorCodes=[UNKNOWN_CHUNK_ID]"));
        assertTrue(output.getAll().contains(
                "invalidFields=[chapters[1].startChunkId]"));
        assertFalse(output.getAll().contains(sensitiveResponse));
        verify(channel).basicAck(19L, false);
    }
}
