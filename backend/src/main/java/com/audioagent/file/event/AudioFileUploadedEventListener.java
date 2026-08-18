package com.audioagent.file.event;

import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.worker.RabbitOutboxMessageSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "audio.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AudioFileUploadedEventListener {

    public static final String HEADER_RETRY_COUNT =
            "x-audio-agent-retry-count";

    private final ObjectMapper objectMapper;
    private final AudioAnalysisTaskService audioAnalysisTaskService;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties outboxProperties;

    @RabbitListener(queues = "#{@outboxQueue.name}")
    public void handle(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventType = header(message,
                    RabbitOutboxMessageSender.HEADER_EVENT_TYPE);
            if (!AudioFileUploadedEventType.AUDIO_FILE_UPLOADED
                    .equals(eventType)) {
                throw new IllegalArgumentException(
                        "Unsupported outbox event type: " + eventType);
            }

            Long eventId = Long.valueOf(header(message,
                    RabbitOutboxMessageSender.HEADER_EVENT_ID));
            AudioFileUploadedEvent event = objectMapper.readValue(
                    message.getBody(), AudioFileUploadedEvent.class);
            validate(event);
            audioAnalysisTaskService.createTaskFromUploadedFile(
                    event.audioFileId(), event.userId(), eventId);
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Invalid uploaded audio outbox event; routing to DLQ, "
                            + "messageId={}, causeType={}",
                    messageId(message), e.getClass().getSimpleName());
            routeToDlq(message, channel, deliveryTag);
        } catch (BusinessException e) {
            log.error("Non-retryable uploaded audio outbox event; routing "
                            + "to DLQ, messageId={}, code={}",
                    messageId(message), e.getCode());
            routeToDlq(message, channel, deliveryTag);
        } catch (Exception e) {
            log.error("Retryable uploaded audio outbox event failure, "
                    + "messageId={}", messageId(message), e);
            routeToRetryOrDlq(message, channel, deliveryTag);
        }
    }

    private void routeToRetryOrDlq(
            Message original, Channel channel, long deliveryTag) {
        int retryCount = retryCount(original);
        if (retryCount >= outboxProperties.getConsumerMaxRetryCount()) {
            log.warn("Outbox consumer retry limit reached; routing to DLQ, "
                            + "messageId={}, retryCount={}",
                    messageId(original), retryCount);
            routeToDlq(original, channel, deliveryTag);
            return;
        }

        int nextRetryCount = retryCount + 1;
        Message retryMessage = copyForPublish(original, nextRetryCount);
        if (publishConfirmed(
                outboxProperties.getConsumerRetryRoutingKey(),
                retryMessage, "retry")) {
            log.warn("Outbox event scheduled for bounded retry, messageId={}, "
                            + "retryCount={}/{}",
                    messageId(original), nextRetryCount,
                    outboxProperties.getConsumerMaxRetryCount());
            basicAck(channel, deliveryTag);
        } else {
            preserveOriginalDelivery(channel, deliveryTag);
        }
    }

    private void routeToDlq(
            Message original, Channel channel, long deliveryTag) {
        Message deadLetter = copyForPublish(
                original, retryCount(original));
        if (publishConfirmed(outboxProperties.getConsumerDlqRoutingKey(),
                deadLetter, "dlq")) {
            log.warn("Outbox event routed to DLQ, messageId={}, "
                            + "retryCount={}",
                    messageId(original), retryCount(original));
            basicAck(channel, deliveryTag);
        } else {
            preserveOriginalDelivery(channel, deliveryTag);
        }
    }

    private boolean publishConfirmed(
            String routingKey, Message outbound, String destination) {
        CorrelationData correlation = new CorrelationData(
                messageId(outbound) + ":" + destination + ":"
                        + UUID.randomUUID());
        try {
            rabbitTemplate.send(outboxProperties.getExchange(), routingKey,
                    outbound, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    outboxProperties.getConfirmTimeoutMs(),
                    TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                log.error("Outbox consumer {} publish NACK, messageId={}, "
                                + "reason={}",
                        destination, messageId(outbound),
                        confirm.getReason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.error("Outbox consumer {} publish returned, messageId={}, "
                                + "reply={}",
                        destination, messageId(outbound),
                        correlation.getReturned().getReplyText());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Outbox consumer {} publish failed, messageId={}",
                    destination, messageId(outbound), e);
            return false;
        }
    }

    private Message copyForPublish(Message original, int retryCount) {
        String eventId = eventIdIfPresent(original);
        return MessageBuilder.withBody(original.getBody())
                .copyHeaders(original.getMessageProperties().getHeaders())
                .setContentType(original.getMessageProperties()
                        .getContentType())
                .setContentEncoding(original.getMessageProperties()
                        .getContentEncoding())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(eventId != null ? eventId
                        : original.getMessageProperties().getMessageId())
                .setHeader(HEADER_RETRY_COUNT, retryCount)
                .build();
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders()
                .get(HEADER_RETRY_COUNT);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.toString()));
        } catch (NumberFormatException e) {
            log.warn("Invalid outbox consumer retry header; treating as "
                            + "retry limit, messageId={}, value={}",
                    messageId(message), value);
            return outboxProperties.getConsumerMaxRetryCount();
        }
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing outbox message header: " + name);
        }
        return value.toString();
    }

    private String eventIdIfPresent(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(
                RabbitOutboxMessageSender.HEADER_EVENT_ID);
        return value == null || value.toString().isBlank()
                ? null : value.toString();
    }

    private String messageId(Message message) {
        String eventId = eventIdIfPresent(message);
        if (eventId != null) {
            return eventId;
        }
        String messageId = message.getMessageProperties().getMessageId();
        return messageId == null ? "unknown" : messageId;
    }

    private void validate(AudioFileUploadedEvent event) {
        if (event.audioFileId() == null || event.audioFileId() <= 0
                || event.userId() == null || event.userId() <= 0
                || event.eventVersion()
                != AudioFileUploadedEvent.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Invalid AUDIO_FILE_UPLOADED payload");
        }
    }

    private void basicAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to ack uploaded audio outbox event", e);
        }
    }

    private void preserveOriginalDelivery(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception e) {
            log.error("Failed to preserve uploaded audio outbox delivery", e);
        }
    }
}
