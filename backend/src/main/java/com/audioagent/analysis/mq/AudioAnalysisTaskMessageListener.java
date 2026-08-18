package com.audioagent.analysis.mq;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.executor.AudioAnalysisTaskExecutor;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "rabbit"
)
@RequiredArgsConstructor
public class AudioAnalysisTaskMessageListener {

    private static final long DEFERRAL_CONFIRM_TIMEOUT_SECONDS = 10;

    private final AudioAnalysisTaskExecutor executor;
    private final AudioAnalysisTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AnalysisProperties analysisProperties;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = AudioAnalysisRabbitConstants.TASK_QUEUE)
    public void handleMessage(
            @org.springframework.messaging.handler.annotation.Payload
            AudioAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {

        Long taskId = taskMessage.getTaskId();
        String messageId = taskMessage.getMessageId();

        log.info(
                "Received message, messageId={}, taskId={}, retryCount={}",
                messageId, taskId,
                taskMessage.getRetryCount()
        );

        if (taskId == null || taskId <= 0) {
            log.error("Invalid taskId, messageId={}", messageId);
            basicAck(message, channel);
            return;
        }

        String executionToken = executionToken(messageId);
        try {
            AudioAnalysisTask task = lookupTask(taskId);
            if (ackTerminalState(task, taskMessage, message, channel)) {
                return;
            }

            boolean staleRecovered = false;
            if (task.getStatus() == AnalysisTaskStatus.PROCESSING) {
                if (!isProcessingStale(task)) {
                    handleActiveProcessing(task, taskMessage,
                            message, channel);
                    return;
                }
                staleRecovered = recoverStaleProcessing(
                        task, executionToken);
                if (!staleRecovered) {
                    handleClaimConflict(taskMessage, message, channel);
                    return;
                }
                log.warn("Recovered stale analysis task, taskId={}, "
                                + "previousExecutionToken={}, "
                                + "newExecutionToken={}",
                        taskId, task.getLastMessageId(), executionToken);
            }

            if (staleRecovered) {
                executor.executeClaimed(taskId, task.getAudioFileId(),
                        executionToken);
            } else {
                executor.execute(taskId, task.getAudioFileId(),
                        executionToken);
            }

            log.info(
                    "Task completed successfully, taskId={}, messageId={}",
                    taskId, messageId
            );
            basicAck(message, channel);

        } catch (AudioAnalysisException e) {
            if (AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED
                    .equals(e.getErrorCode())) {
                handleClaimConflict(taskMessage, message, channel);
                return;
            }
            handleAnalysisException(
                    taskMessage, message, channel, taskId, messageId,
                    executionToken, e);

        } catch (Exception e) {
            log.error(
                    "Unexpected error in consumer, taskId={}, messageId={}",
                    taskId, messageId, e
            );
            basicNack(message, channel, false);
        }
    }

    private void handleAnalysisException(
            AudioAnalysisTaskMessage taskMessage,
            Message message, Channel channel,
            Long taskId, String messageId,
            String executionToken,
            AudioAnalysisException e) {

        log.error(
                "Analysis failed, taskId={}, messageId={}, errorCode={}, retryable={}",
                taskId, messageId, e.getErrorCode(), e.isRetryable(), e
        );

        if (!e.isRetryable()) {
            failAndDeadLetter(taskMessage, message, channel,
                    taskId, executionToken,
                    AnalysisTaskStatus.PROCESSING, e);
            return;
        }

        int currentRetry = taskMessage.getRetryCount() != null
                ? taskMessage.getRetryCount() : 0;
        int maxRetries = analysisProperties.getRetry()
                .getMaxAttempts();

        if (currentRetry >= maxRetries) {
            log.warn(
                    "Max retry reached, taskId={}, retryCount={}, maxRetry={}",
                    taskId, currentRetry, maxRetries
            );
            failAndDeadLetter(taskMessage, message, channel,
                    taskId, executionToken,
                    AnalysisTaskStatus.PROCESSING, e);
            return;
        }

        scheduleRetry(taskMessage, message, channel, taskId,
                executionToken, e, currentRetry);
    }

    private void scheduleRetry(
            AudioAnalysisTaskMessage taskMessage,
            Message message, Channel channel,
            Long taskId, String executionToken,
            AudioAnalysisException e,
            int currentRetry) {

        int newRetryCount = currentRetry + 1;
        String errorMessage = truncate(e.getMessage(), 1000);
        int delayMs = analysisProperties.getRetry()
                .getDelayMilliseconds();
        LocalDateTime nextRetryAt =
                LocalDateTime.now().plusNanos(delayMs * 1_000_000L);

        boolean updated = Boolean.TRUE.equals(
                transactionTemplate.execute(s -> {
                    LambdaUpdateWrapper<AudioAnalysisTask> w =
                            new LambdaUpdateWrapper<>();
                    w.eq(AudioAnalysisTask::getId, taskId)
                            .eq(AudioAnalysisTask::getStatus,
                                    AnalysisTaskStatus.PROCESSING)
                            .eq(AudioAnalysisTask::getLastMessageId,
                                    executionToken)
                            .set(AudioAnalysisTask::getStatus,
                                    AnalysisTaskStatus.PENDING)
                            .set(AudioAnalysisTask::getProgress, 0)
                            .set(AudioAnalysisTask::getRetryCount,
                                    newRetryCount)
                            .set(AudioAnalysisTask::getErrorMessage,
                                    errorMessage)
                            .set(AudioAnalysisTask::getLastErrorCode,
                                    e.getErrorCode())
                            .set(AudioAnalysisTask::getNextRetryAt,
                                    nextRetryAt)
                            .set(AudioAnalysisTask::getLastMessageId,
                                    executionToken)
                            .set(AudioAnalysisTask::getUpdatedAt,
                                    LocalDateTime.now());

                    int rows = taskMapper.update(null, w);
                    return rows == 1;
                })
        );

        if (!updated) {
            log.error(
                    "Failed to update retry state, taskId={}, "
                            + "status may not be PROCESSING",
                    taskId
            );
            basicAck(message, channel);
            return;
        }

        AudioAnalysisTaskMessage retryMsg =
                AudioAnalysisTaskMessage.retryDispatch(
                        taskMessage, newRetryCount);

        try {
            rabbitTemplate.convertAndSend(
                    AudioAnalysisRabbitConstants.RETRY_EXCHANGE,
                    AudioAnalysisRabbitConstants.RETRY_ROUTING_KEY,
                    retryMsg,
                    new CorrelationData(retryMsg.getMessageId())
            );

            log.info(
                    "Retry scheduled, taskId={}, retryCount={}, "
                            + "nextRetryAt={}, newMessageId={}",
                    taskId, newRetryCount, nextRetryAt,
                    retryMsg.getMessageId()
            );

            basicAck(message, channel);

        } catch (Exception pubEx) {
            log.error(
                    "Failed to publish retry message, taskId={}, "
                            + "retryCount={}, falling back to FAILED",
                    taskId, newRetryCount, pubEx
            );
            /*
             * 重试消息发布失败，将任务标记为 FAILED 并发送死信。
             */
            AudioAnalysisException fallbackEx =
                    new AudioAnalysisException(
                            AudioAnalysisException.ErrorCodes
                                    .RETRY_MESSAGE_PUBLISH_FAILED,
                            false,
                            "重试消息发布失败",
                            pubEx
                    );
            failAndDeadLetter(taskMessage, message, channel,
                    taskId, executionToken,
                    AnalysisTaskStatus.PENDING, fallbackEx);
        }
    }

    private void failAndDeadLetter(
            AudioAnalysisTaskMessage taskMessage,
            Message message, Channel channel,
            Long taskId, String executionToken,
            AnalysisTaskStatus expectedStatus,
            AudioAnalysisException e) {

        String errorMessage = truncate(e.getMessage(), 1000);

        boolean updated = Boolean.TRUE.equals(
                transactionTemplate.execute(s -> {
                    LambdaUpdateWrapper<AudioAnalysisTask> w =
                            new LambdaUpdateWrapper<>();
                    w.eq(AudioAnalysisTask::getId, taskId)
                            .eq(AudioAnalysisTask::getStatus,
                                    expectedStatus)
                            .eq(AudioAnalysisTask::getLastMessageId,
                                    executionToken)
                            .set(AudioAnalysisTask::getStatus,
                                    AnalysisTaskStatus.FAILED)
                            .set(AudioAnalysisTask::getProgress, 0)
                            .set(AudioAnalysisTask::getErrorMessage,
                                    errorMessage)
                            .set(AudioAnalysisTask::getLastErrorCode,
                                    e.getErrorCode())
                            .set(AudioAnalysisTask::getNextRetryAt, null)
                            .set(AudioAnalysisTask::getFinishedAt,
                                    LocalDateTime.now())
                            .set(AudioAnalysisTask::getUpdatedAt,
                                    LocalDateTime.now());
                    return taskMapper.update(null, w) == 1;
                }));

        if (!updated) {
            log.info("Ignored failure from a consumer that no longer owns "
                            + "the task, taskId={}, executionToken={}",
                    taskId, executionToken);
            handleClaimConflict(taskMessage, message, channel);
            return;
        }

        AudioAnalysisTaskMessage deadMsg =
                AudioAnalysisTaskMessage.deadLetter(
                        taskMessage,
                        e.getErrorCode(),
                        errorMessage
                );

        try {
            rabbitTemplate.convertAndSend(
                    AudioAnalysisRabbitConstants.DEAD_EXCHANGE,
                    AudioAnalysisRabbitConstants.DEAD_ROUTING_KEY,
                    deadMsg,
                    new CorrelationData(deadMsg.getMessageId())
            );

            log.info(
                    "Dead letter published, taskId={}, "
                            + "errorCode={}, deadMessageId={}",
                    taskId, e.getErrorCode(),
                    deadMsg.getMessageId()
            );

            basicAck(message, channel);

        } catch (Exception pubEx) {
            log.error(
                    "CRITICAL: Failed to publish dead letter, "
                            + "taskId={}, errorCode={}",
                    taskId, e.getErrorCode(), pubEx
            );
            basicAck(message, channel);
        }
    }

    private AudioAnalysisTask lookupTask(Long taskId) {
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.TASK_NOT_FOUND,
                    false, "分析任务不存在");
        }
        return task;
    }

    private boolean ackTerminalState(
            AudioAnalysisTask task,
            AudioAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {
        if (task.getStatus() == AnalysisTaskStatus.SUCCESS) {
            log.info("Duplicate message for successful task acknowledged, "
                            + "taskId={}, messageId={}",
                    task.getId(), taskMessage.getMessageId());
            basicAck(message, channel);
            return true;
        }
        if (task.getStatus() == AnalysisTaskStatus.FAILED) {
            log.info("Old message for failed task acknowledged; manual retry "
                            + "is required, taskId={}, messageId={}",
                    task.getId(), taskMessage.getMessageId());
            basicAck(message, channel);
            return true;
        }
        return false;
    }

    private void handleActiveProcessing(
            AudioAnalysisTask task,
            AudioAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {
        if (sameLogicalMessage(task.getLastMessageId(),
                taskMessage.getMessageId())) {
            deferInFlightRedelivery(taskMessage, message, channel);
            return;
        }
        log.info("Concurrent duplicate acknowledged while task is being "
                        + "processed, taskId={}, messageId={}, owner={}",
                task.getId(), taskMessage.getMessageId(),
                task.getLastMessageId());
        basicAck(message, channel);
    }

    private boolean isProcessingStale(AudioAnalysisTask task) {
        LocalDateTime leaseTime = task.getUpdatedAt() != null
                ? task.getUpdatedAt() : task.getStartedAt();
        if (leaseTime == null) {
            return false;
        }
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(
                analysisProperties.getProcessingLeaseSeconds());
        return !leaseTime.isAfter(staleBefore);
    }

    private boolean recoverStaleProcessing(
            AudioAnalysisTask observed,
            String newExecutionToken) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(
                analysisProperties.getProcessingLeaseSeconds());
        LambdaUpdateWrapper<AudioAnalysisTask> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(AudioAnalysisTask::getId, observed.getId())
                .eq(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PROCESSING);
        if (observed.getLastMessageId() == null) {
            wrapper.isNull(AudioAnalysisTask::getLastMessageId);
        } else {
            wrapper.eq(AudioAnalysisTask::getLastMessageId,
                    observed.getLastMessageId());
        }
        if (observed.getUpdatedAt() != null) {
            wrapper.le(AudioAnalysisTask::getUpdatedAt, staleBefore);
        } else if (observed.getStartedAt() != null) {
            wrapper.isNull(AudioAnalysisTask::getUpdatedAt)
                    .le(AudioAnalysisTask::getStartedAt, staleBefore);
        } else {
            return false;
        }
        wrapper.set(AudioAnalysisTask::getLastMessageId,
                        newExecutionToken)
                .set(AudioAnalysisTask::getStartedAt, now)
                .set(AudioAnalysisTask::getUpdatedAt, now)
                .set(AudioAnalysisTask::getProgress, 10);
        return taskMapper.update(null, wrapper) == 1;
    }

    private void handleClaimConflict(
            AudioAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {
        AudioAnalysisTask latest = taskMapper.selectById(
                taskMessage.getTaskId());
        if (latest == null) {
            log.warn("Task disappeared while resolving claim conflict, "
                            + "taskId={}, messageId={}",
                    taskMessage.getTaskId(), taskMessage.getMessageId());
            basicNack(message, channel, false);
            return;
        }
        if (ackTerminalState(latest, taskMessage, message, channel)) {
            return;
        }
        if (latest.getStatus() == AnalysisTaskStatus.PROCESSING) {
            log.info("Claim lost to another consumer; duplicate delivery "
                            + "acknowledged, taskId={}, messageId={}, owner={}",
                    latest.getId(), taskMessage.getMessageId(),
                    latest.getLastMessageId());
            basicAck(message, channel);
            return;
        }
        log.info("Claim conflict left task pending; delivery deferred, "
                        + "taskId={}, messageId={}",
                taskMessage.getTaskId(), taskMessage.getMessageId());
        deferInFlightRedelivery(taskMessage, message, channel);
    }

    private void deferInFlightRedelivery(
            AudioAnalysisTaskMessage taskMessage,
            Message message,
            Channel channel) {
        try {
            CorrelationData correlationData =
                    new CorrelationData(taskMessage.getMessageId());
            rabbitTemplate.convertAndSend(
                    AudioAnalysisRabbitConstants.RETRY_EXCHANGE,
                    AudioAnalysisRabbitConstants.RETRY_ROUTING_KEY,
                    taskMessage,
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(DEFERRAL_CONFIRM_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException(
                        "Broker did not confirm in-flight deferral: "
                                + confirm.getReason());
            }
            log.info("In-flight delivery deferred without changing task "
                            + "retry state, taskId={}, messageId={}",
                    taskMessage.getTaskId(), taskMessage.getMessageId());
            basicAck(message, channel);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to defer in-flight delivery, taskId={}, "
                            + "messageId={}",
                    taskMessage.getTaskId(), taskMessage.getMessageId(), e);
            basicNack(message, channel, true);
        }
    }

    private String executionToken(String messageId) {
        String stableMessageId = messageId == null || messageId.isBlank()
                ? "unknown" : messageId;
        return stableMessageId + "|" + UUID.randomUUID();
    }

    private boolean sameLogicalMessage(String executionToken,
                                       String messageId) {
        if (executionToken == null || messageId == null) {
            return executionToken == null;
        }
        return executionToken.equals(messageId)
                || executionToken.startsWith(messageId + "|");
    }

    private void basicAck(Message message, Channel channel) {
        try {
            channel.basicAck(
                    message.getMessageProperties().getDeliveryTag(),
                    false);
        } catch (Exception e) {
            log.error("Failed to ack message, deliveryTag={}",
                    message.getMessageProperties().getDeliveryTag(), e);
        }
    }

    private void basicNack(Message message, Channel channel,
                           boolean requeue) {
        try {
            channel.basicNack(
                    message.getMessageProperties().getDeliveryTag(),
                    false, requeue);
        } catch (Exception e) {
            log.error("Failed to nack message, deliveryTag={}",
                    message.getMessageProperties().getDeliveryTag(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen
                ? s.substring(0, maxLen - 3) + "..."
                : s;
    }
}
