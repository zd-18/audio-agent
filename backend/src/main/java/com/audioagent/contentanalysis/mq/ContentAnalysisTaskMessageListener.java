package com.audioagent.contentanalysis.mq;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisErrorClassifier;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.executor.AudioContentAnalysisTaskExecutor;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "audio-agent.ai.deepseek.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ContentAnalysisTaskMessageListener {

    private final AudioContentAnalysisTaskExecutor executor;
    private final AudioContentAnalysisTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DeepSeekProperties properties;
    private final ContentAnalysisErrorClassifier errorClassifier;

    @RabbitListener(queues = ContentAnalysisRabbitConstants.TASK_QUEUE)
    public void handle(
            @org.springframework.messaging.handler.annotation.Payload
            ContentAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {
        Long taskId = taskMessage == null ? null : taskMessage.taskId();
        if (taskId == null || taskId <= 0) {
            log.warn("Invalid content analysis message ignored");
            ack(message, channel);
            return;
        }
        if (taskMapper.claim(taskId, LocalDateTime.now()) != 1) {
            log.info("Duplicate content analysis message ignored, "
                    + "taskId={}", taskId);
            ack(message, channel);
            return;
        }
        AudioContentAnalysisTask task = taskMapper.selectById(taskId);
        try {
            executor.execute(taskId);
            ack(message, channel);
        } catch (Throwable error) {
            ContentAnalysisException failure =
                    errorClassifier.classify(error);
            logFailure(taskId, task, error, failure);
            handleFailure(taskMessage, task, message, channel, failure);
        }
    }

    private void logFailure(Long taskId,
                            AudioContentAnalysisTask task,
                            Throwable error,
                            ContentAnalysisException failure) {
        Throwable rootCause = rootCause(error);
        SQLException sqlException = sqlException(error);
        AnalysisResultValidationException validation =
                validationException(error);
        var diagnostics = validation == null
                ? null : validation.getResponseDiagnostics();
        String rootCauseMessage = sqlException == null
                ? safeLogValue(failure.getMessage())
                : safeLogValue(sqlException.getMessage());
        log.warn("Content analysis execution failed, taskId={}, "
                        + "transcriptId={}, userId={}, modelName={}, "
                        + "promptVersion={}, exceptionType={}, "
                        + "rootCauseType={}, SQLState={}, vendorCode={}, "
                        + "rootCauseMessage={}, failureCode={}, retryable={}, "
                        + "stage={}, diagnosticCode={}, sourceChunkCount={}, "
                        + "allowedChunkIds={}, summaryPresent={}, "
                        + "keyPointCount={}, chapterCount={}, "
                        + "speechIssueCount={}, errorCodes={}, "
                        + "invalidFields={}, responseLength={}, "
                        + "responseSha256={}, topLevelFieldNames={}, "
                        + "firstCharacterType={}, lastCharacterType={}, "
                        + "markdownCodeFencePresent={}",
                taskId,
                task == null ? null : task.getTranscriptId(),
                task == null ? null : task.getUserId(),
                task == null ? null : task.getModelName(),
                task == null ? null : task.getPromptVersion(),
                error.getClass().getName(),
                rootCause.getClass().getName(),
                sqlException == null ? null : sqlException.getSQLState(),
                sqlException == null ? null : sqlException.getErrorCode(),
                rootCauseMessage,
                failure.getErrorCode().name(),
                failure.isRetryable(),
                validation == null ? null : validation.getStage(),
                validation == null ? null
                        : validation.getDiagnosticCode(),
                validation == null ? null
                        : validation.getAllowedChunkIds().size(),
                validation == null ? null
                        : validation.getAllowedChunkIds(),
                diagnostics == null ? null
                        : diagnostics.summaryPresent(),
                diagnostics == null ? null
                        : diagnostics.keyPointCount(),
                diagnostics == null ? null
                        : diagnostics.chapterCount(),
                diagnostics == null ? null
                        : diagnostics.speechIssueCount(),
                validation == null ? null
                        : validation.getErrorCodes(),
                validation == null ? null
                        : validation.getInvalidFields(),
                diagnostics == null ? null
                        : diagnostics.responseLength(),
                diagnostics == null ? null
                        : diagnostics.responseSha256(),
                diagnostics == null ? null
                        : diagnostics.topLevelFieldNames(),
                diagnostics == null ? null
                        : diagnostics.firstCharacterType(),
                diagnostics == null ? null
                        : diagnostics.lastCharacterType(),
                diagnostics == null ? null
                        : diagnostics.markdownCodeFencePresent());
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private SQLException sqlException(Throwable error) {
        SQLException result = null;
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException candidate) {
                result = candidate;
            }
            current = current.getCause();
        }
        return result;
    }

    private AnalysisResultValidationException validationException(
            Throwable error) {
        AnalysisResultValidationException result = null;
        Throwable current = error;
        while (current != null) {
            if (current
                    instanceof AnalysisResultValidationException candidate) {
                result = candidate;
            }
            current = current.getCause();
        }
        return result;
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 1000
                ? sanitized : sanitized.substring(0, 1000);
    }

    private void handleFailure(ContentAnalysisTaskMessage source,
                               AudioContentAnalysisTask task,
                               Message message,
                               Channel channel,
                               ContentAnalysisException failure) {
        int retryCount = task == null || task.getRetryCount() == null
                ? 0 : task.getRetryCount();
        if (failure.isRetryable()
                && retryCount < properties.getMaxRetryCount()) {
            scheduleRetry(source, message, channel, failure,
                    retryCount + 1);
            return;
        }
        taskMapper.markFailed(source.taskId(),
                failure.getErrorCode().name(),
                safeMessage(failure), LocalDateTime.now());
        publishDeadLetter(source);
        ack(message, channel);
    }

    private void scheduleRetry(
            ContentAnalysisTaskMessage source,
            Message message,
            Channel channel,
            ContentAnalysisException failure,
            int nextRetryCount) {
        if (taskMapper.scheduleRetry(
                source.taskId(), nextRetryCount,
                failure.getErrorCode().name(), safeMessage(failure),
                LocalDateTime.now()) != 1) {
            ack(message, channel);
            return;
        }
        long delay = retryDelay(nextRetryCount,
                failure.getRetryAfterMilliseconds());
        try {
            rabbitTemplate.convertAndSend(
                    ContentAnalysisRabbitConstants.RETRY_EXCHANGE,
                    ContentAnalysisRabbitConstants.RETRY_ROUTING_KEY,
                    source,
                    outgoing -> {
                        outgoing.getMessageProperties().setExpiration(
                                Long.toString(delay));
                        return outgoing;
                    },
                    new CorrelationData(
                            UUID.randomUUID().toString()));
            ack(message, channel);
        } catch (RuntimeException publishError) {
            taskMapper.markFailed(source.taskId(),
                    ErrorCode.AI_SERVICE_UNAVAILABLE.name(),
                    "智能分析重试任务提交失败",
                    LocalDateTime.now());
            publishDeadLetter(source);
            ack(message, channel);
        }
    }

    private long retryDelay(int retryCount, Long retryAfter) {
        if (retryAfter != null) {
            return Math.max(100L, Math.min(retryAfter, 300_000L));
        }
        long multiplier = 1L << Math.min(10,
                Math.max(0, retryCount - 1));
        return Math.min(300_000L,
                properties.getRetryDelayMilliseconds() * multiplier);
    }

    private void publishDeadLetter(ContentAnalysisTaskMessage source) {
        try {
            rabbitTemplate.convertAndSend(
                    ContentAnalysisRabbitConstants.DEAD_EXCHANGE,
                    ContentAnalysisRabbitConstants.DEAD_ROUTING_KEY,
                    source,
                    new CorrelationData(
                            UUID.randomUUID().toString()));
        } catch (RuntimeException e) {
            log.error("Content analysis dead-letter publish failed, "
                    + "taskId={}, exceptionType={}",
                    source.taskId(), e.getClass().getName());
        }
    }

    private String safeMessage(ContentAnalysisException failure) {
        String value = failure.getMessage();
        if (value == null || value.isBlank()) {
            value = failure.getErrorCode().getMessage();
        }
        return value.length() <= 500
                ? value : value.substring(0, 500);
    }

    private void ack(Message message, Channel channel) {
        try {
            channel.basicAck(message.getMessageProperties()
                    .getDeliveryTag(), false);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to acknowledge content analysis message", e);
        }
    }
}
