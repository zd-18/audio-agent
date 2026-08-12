package com.audioagent.processing.mq;

import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.exception.ProcessingExecutionErrorClassifier;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.executor.AudioProcessingExecutionExecutor;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.model.ProcessingExecutionStatus;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.processing.enabled",
        havingValue = "true", matchIfMissing = true)
public class AudioProcessingExecutionMessageListener {

    private final AudioProcessingExecutionExecutor executor;
    private final AudioProcessingExecutionMapper executionMapper;
    private final AudioProcessingExecutionStepMapper executionStepMapper;
    private final ProcessingExecutionErrorClassifier errorClassifier;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = AudioProcessingRabbitConstants.QUEUE)
    public void handle(AudioProcessingExecutionMessage payload,
                       Message message, Channel channel) {
        Long executionId = payload == null ? null : payload.getExecutionId();
        if (executionId == null || executionId <= 0) {
            log.error("Invalid processing execution message received");
            ack(message, channel);
            return;
        }
        try {
            executor.execute(executionId);
            ack(message, channel);
        } catch (Throwable error) {
            ProcessingExecutionException classified =
                    errorClassifier.classify(error);
            handleFailure(executionId, classified, message, channel);
        }
    }

    private void handleFailure(Long executionId,
                               ProcessingExecutionException error,
                               Message message, Channel channel) {
        AudioProcessingExecution execution = executionMapper
                .selectExecutionById(executionId);
        if (execution == null || !ProcessingExecutionStatus.PROCESSING.name()
                .equals(execution.getExecutionStatus())) {
            ack(message, channel);
            return;
        }
        if (!error.isRetryable()) {
            markTerminal(executionId, ProcessingExecutionStatus.FAILED,
                    error);
            ack(message, channel);
            return;
        }
        int retryCount = execution.getRetryCount() == null
                ? 0 : execution.getRetryCount();
        int maxRetries = execution.getMaxRetryCount() == null
                ? 0 : execution.getMaxRetryCount();
        if (retryCount >= maxRetries) {
            markTerminal(executionId,
                    ProcessingExecutionStatus.DEAD_LETTER, error);
            publishDeadLetter(executionId);
            ack(message, channel);
            return;
        }
        scheduleRetry(executionId, retryCount + 1, error,
                message, channel);
    }

    private void scheduleRetry(Long executionId, int retryCount,
                               ProcessingExecutionException error,
                               Message source, Channel channel) {
        LocalDateTime now = LocalDateTime.now();
        Boolean updated = transactionTemplate.execute(status -> {
            int rows = executionMapper.scheduleRetry(executionId,
                    retryCount, error.getFailureCode(),
                    userMessage(error), now);
            if (rows == 1) {
                executionStepMapper.resetForRetry(executionId, now);
            }
            return rows == 1;
        });
        if (!Boolean.TRUE.equals(updated)) {
            ack(source, channel);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    AudioProcessingRabbitConstants.EXCHANGE,
                    AudioProcessingRabbitConstants.RETRY_ROUTING_KEY,
                    new AudioProcessingExecutionMessage(executionId));
            log.info("Processing execution retry scheduled, executionId={}, retryCount={}",
                    executionId, retryCount);
            ack(source, channel);
        } catch (RuntimeException publishError) {
            LocalDateTime failedAt = LocalDateTime.now();
            executionMapper.markQueuedFailure(executionId,
                    error.getFailureCode(),
                    "Execution retry message could not be queued", failedAt);
            executionStepMapper.markUnfinishedFailed(executionId,
                    "Execution retry message could not be queued", failedAt);
            log.error("Processing retry publish failed, executionId={}",
                    executionId, publishError);
            ack(source, channel);
        }
    }

    private void markTerminal(Long executionId,
                              ProcessingExecutionStatus status,
                              ProcessingExecutionException error) {
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.execute(transaction -> {
            executionMapper.markFailed(executionId, status.name(),
                    error.getFailureCode(), userMessage(error), now);
            executionStepMapper.markUnfinishedFailed(executionId,
                    userMessage(error), now);
            return null;
        });
        log.info("Processing execution reached terminal failure, executionId={}, status={}, failureCode={}",
                executionId, status, error.getFailureCode());
    }

    private void publishDeadLetter(Long executionId) {
        try {
            rabbitTemplate.convertAndSend(
                    AudioProcessingRabbitConstants.EXCHANGE,
                    AudioProcessingRabbitConstants.DEAD_LETTER_ROUTING_KEY,
                    new AudioProcessingExecutionMessage(executionId));
        } catch (RuntimeException e) {
            log.error("Processing dead letter publish failed, executionId={}",
                    executionId, e);
        }
    }

    private String userMessage(ProcessingExecutionException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Audio processing execution failed";
        }
        return message.length() <= 500 ? message
                : message.substring(0, 497) + "...";
    }

    private void ack(Message message, Channel channel) {
        try {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),
                    false);
        } catch (Exception e) {
            log.error("Processing message acknowledgement failed", e);
        }
    }
}
