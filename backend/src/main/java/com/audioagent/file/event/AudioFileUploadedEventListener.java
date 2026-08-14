package com.audioagent.file.event;

import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.outbox.worker.RabbitOutboxMessageSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "audio.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AudioFileUploadedEventListener {

    private final ObjectMapper objectMapper;
    private final AudioAnalysisTaskService audioAnalysisTaskService;

    @RabbitListener(queues = "#{@outboxQueue.name}")
    public void handle(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventType = header(message,
                    RabbitOutboxMessageSender.HEADER_EVENT_TYPE);
            if (!AudioFileUploadedEventType.AUDIO_FILE_UPLOADED
                    .equals(eventType)) {
                log.warn("Ignoring unsupported outbox event type: {}",
                        eventType);
                channel.basicAck(deliveryTag, false);
                return;
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
            log.error("Discarding invalid uploaded audio outbox event", e);
            basicNack(channel, deliveryTag, false);
        } catch (BusinessException e) {
            log.error("Discarding inconsistent uploaded audio outbox event", e);
            basicNack(channel, deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to consume uploaded audio outbox event", e);
            basicNack(channel, deliveryTag, true);
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

    private void validate(AudioFileUploadedEvent event) {
        if (event.audioFileId() == null || event.audioFileId() <= 0
                || event.userId() == null || event.userId() <= 0
                || event.eventVersion()
                != AudioFileUploadedEvent.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Invalid AUDIO_FILE_UPLOADED payload");
        }
    }

    private void basicNack(Channel channel, long deliveryTag,
                           boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (Exception ackError) {
            log.error("Failed to nack uploaded audio outbox event", ackError);
        }
    }
}
