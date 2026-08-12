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

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "rabbit"
)
@RequiredArgsConstructor
public class AudioAnalysisTaskMessageListener {

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

        try {
            updateLastMessageId(taskId, messageId);
            executor.execute(taskId, lookupAudioFileId(taskId));

            log.info(
                    "Task completed successfully, taskId={}, messageId={}",
                    taskId, messageId
            );
            basicAck(message, channel);

        } catch (AudioAnalysisException e) {
            handleAnalysisException(
                    taskMessage, message, channel, taskId, messageId, e);

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
            AudioAnalysisException e) {

        log.error(
                "Analysis failed, taskId={}, messageId={}, errorCode={}, retryable={}",
                taskId, messageId, e.getErrorCode(), e.isRetryable(), e
        );

        if (!e.isRetryable()) {
            failAndDeadLetter(taskMessage, message, channel,
                    taskId, e);
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
                    taskId, e);
            return;
        }

        scheduleRetry(taskMessage, message, channel, taskId, e,
                currentRetry);
    }

    private void scheduleRetry(
            AudioAnalysisTaskMessage taskMessage,
            Message message, Channel channel,
            Long taskId, AudioAnalysisException e,
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
                                    taskMessage.getMessageId())
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
                    taskId, fallbackEx);
        }
    }

    private void failAndDeadLetter(
            AudioAnalysisTaskMessage taskMessage,
            Message message, Channel channel,
            Long taskId, AudioAnalysisException e) {

        String errorMessage = truncate(e.getMessage(), 1000);

        transactionTemplate.execute(s -> {
            LambdaUpdateWrapper<AudioAnalysisTask> w =
                    new LambdaUpdateWrapper<>();
            w.eq(AudioAnalysisTask::getId, taskId)
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
            taskMapper.update(null, w);
            return null;
        });

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

    private void updateLastMessageId(Long taskId, String messageId) {
        LambdaUpdateWrapper<AudioAnalysisTask> w =
                new LambdaUpdateWrapper<>();
        w.eq(AudioAnalysisTask::getId, taskId)
                .set(AudioAnalysisTask::getLastMessageId, messageId);
        taskMapper.update(null, w);
    }

    private Long lookupAudioFileId(Long taskId) {
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.TASK_NOT_FOUND,
                    false, "分析任务不存在");
        }
        return task.getAudioFileId();
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
