package com.audioagent.transcription.mq;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.exception.TranscriptionErrorClassifier;
import com.audioagent.transcription.exception.TranscriptionException;
import com.audioagent.transcription.executor.AudioTranscriptionTaskExecutor;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
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
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "audio.transcription.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TranscriptionTaskMessageListener {

    private final AudioTranscriptionTaskExecutor executor;
    private final AudioTranscriptionTaskMapper taskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final TranscriptionProperties properties;
    private final TranscriptionErrorClassifier errorClassifier;

    @RabbitListener(queues = TranscriptionRabbitConstants.TASK_QUEUE)
    public void handle(
            @org.springframework.messaging.handler.annotation.Payload
            TranscriptionTaskMessage taskMessage,
            Message message,
            Channel channel) {
        Long taskId = taskMessage == null ? null : taskMessage.getTaskId();
        if (taskId == null || taskId <= 0) {
            log.warn("Invalid transcription message ignored");
            ack(message, channel);
            return;
        }

        if (taskMapper.claim(taskId, LocalDateTime.now()) != 1) {
            log.info("Duplicate transcription message ignored, taskId={}",
                    taskId);
            ack(message, channel);
            return;
        }

        try {
            executor.execute(taskId);
            ack(message, channel);
        } catch (Throwable error) {
            TranscriptionException failure = classify(error);
            log.error("Transcription execution failed, taskId={}, "
                            + "stage=LISTENER, exceptionClass={}, "
                            + "exceptionMessage={}, failureCode={}, "
                            + "retryable={}",
                    taskId,
                    error.getClass().getName(),
                    error.getMessage(),
                    failure.getErrorCode().name(),
                    failure.isRetryable(),
                    error);
            handleFailure(taskMessage, message, channel, failure);
        }
    }

    private void handleFailure(TranscriptionTaskMessage source,
                               Message message,
                               Channel channel,
                               TranscriptionException failure) {
        int retryCount = source.getRetryCount() == null
                ? 0 : source.getRetryCount();
        if (failure.isRetryable()
                && retryCount < properties.getMaxRetryCount()) {
            scheduleRetry(source, message, channel, failure,
                    retryCount + 1);
            return;
        }
        failAndDeadLetter(source, message, channel, failure);
    }

    private void scheduleRetry(TranscriptionTaskMessage source,
                               Message message,
                               Channel channel,
                               TranscriptionException failure,
                               int nextRetryCount) {
        String friendlyMessage = truncate(failure.getMessage());
        if (taskMapper.scheduleRetry(source.getTaskId(), nextRetryCount,
                failure.getErrorCode().name(), friendlyMessage,
                LocalDateTime.now()) != 1) {
            ack(message, channel);
            return;
        }
        TranscriptionTaskMessage retry =
                TranscriptionTaskMessage.retry(source, nextRetryCount);
        try {
            rabbitTemplate.convertAndSend(
                    TranscriptionRabbitConstants.RETRY_EXCHANGE,
                    TranscriptionRabbitConstants.RETRY_ROUTING_KEY,
                    retry, new CorrelationData(retry.getMessageId()));
            ack(message, channel);
        } catch (RuntimeException publishError) {
            taskMapper.markFailed(source.getTaskId(),
                    ErrorCode.TRANSCRIPTION_FAILED.name(),
                    "转写任务重试提交失败", LocalDateTime.now());
            publishDeadLetter(source);
            ack(message, channel);
        }
    }

    private void failAndDeadLetter(TranscriptionTaskMessage source,
                                   Message message,
                                   Channel channel,
                                   TranscriptionException failure) {
        taskMapper.markFailed(source.getTaskId(),
                failure.getErrorCode().name(),
                truncate(failure.getMessage()), LocalDateTime.now());
        publishDeadLetter(source);
        ack(message, channel);
    }

    private void publishDeadLetter(TranscriptionTaskMessage source) {
        try {
            rabbitTemplate.convertAndSend(
                    TranscriptionRabbitConstants.DEAD_EXCHANGE,
                    TranscriptionRabbitConstants.DEAD_ROUTING_KEY,
                    source, new CorrelationData(source.getMessageId()));
        } catch (RuntimeException e) {
            log.error("Transcription dead-letter publish failed, taskId={}",
                    source.getTaskId());
        }
    }

    private TranscriptionException classify(Throwable error) {
        if (error instanceof TranscriptionException transcriptionError) {
            return transcriptionError;
        }
        return errorClassifier.classify(error);
    }

    private String truncate(String message) {
        String value = message == null || message.isBlank()
                ? "音频转写失败" : message.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void ack(Message message, Channel channel) {
        try {
            channel.basicAck(message.getMessageProperties()
                    .getDeliveryTag(), false);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to acknowledge transcription message", e);
        }
    }
}
