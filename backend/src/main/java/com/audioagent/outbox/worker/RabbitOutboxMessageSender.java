package com.audioagent.outbox.worker;

import com.audioagent.analysis.mq.AudioAnalysisRabbitConstants;
import com.audioagent.analysis.mq.AudioAnalysisTaskMessage;
import com.audioagent.analysis.outbox.AudioAnalysisTaskDispatchEvent;
import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class RabbitOutboxMessageSender implements OutboxMessageSender {

    public static final String HEADER_EVENT_ID = "x-outbox-event-id";
    public static final String HEADER_AGGREGATE_TYPE = "x-aggregate-type";
    public static final String HEADER_AGGREGATE_ID = "x-aggregate-id";
    public static final String HEADER_EVENT_TYPE = "x-event-type";

    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public CompletableFuture<OutboxBrokerConfirmation> send(
            OutboxEvent event) {
        String eventId = event.getId().toString();
        Destination destination = destination(event);
        Message message = MessageBuilder.withBody(body(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(eventId)
                .setHeader(HEADER_EVENT_ID, eventId)
                .setHeader(HEADER_AGGREGATE_TYPE,
                        event.getAggregateType())
                .setHeader(HEADER_AGGREGATE_ID,
                        event.getAggregateId())
                .setHeader(HEADER_EVENT_TYPE, event.getEventType())
                .build();

        CorrelationData correlationData = new CorrelationData(eventId);
        rabbitTemplate.send(
                destination.exchange(), destination.routingKey(),
                message, correlationData);

        return correlationData.getFuture().thenApply(confirm -> {
            if (!confirm.isAck()) {
                return OutboxBrokerConfirmation.nack(confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                return OutboxBrokerConfirmation.nack(
                        "message was returned as unroutable: "
                                + correlationData.getReturned()
                                .getReplyText());
            }
            return OutboxBrokerConfirmation.ack();
        });
    }

    private Destination destination(OutboxEvent event) {
        if (AudioAnalysisTaskDispatchEvent.EVENT_TYPE.equals(
                event.getEventType())) {
            return new Destination(AudioAnalysisRabbitConstants.EXCHANGE,
                    AudioAnalysisRabbitConstants.TASK_ROUTING_KEY);
        }
        return new Destination(
                properties.getExchange(), properties.getRoutingKey());
    }

    private byte[] body(OutboxEvent event) {
        if (!AudioAnalysisTaskDispatchEvent.EVENT_TYPE.equals(
                event.getEventType())) {
            return event.getPayload().getBytes(StandardCharsets.UTF_8);
        }
        try {
            AudioAnalysisTaskDispatchEvent.Payload payload =
                    objectMapper.readValue(event.getPayload(),
                            AudioAnalysisTaskDispatchEvent.Payload.class);
            if (payload.taskId() == null || payload.taskId() <= 0) {
                throw new IllegalArgumentException(
                        "Invalid analysis task dispatch payload");
            }
            String messageId = event.getId().toString();
            AudioAnalysisTaskMessage message = AudioAnalysisTaskMessage
                    .builder()
                    .taskId(payload.taskId())
                    .messageId(messageId)
                    .originalMessageId(messageId)
                    .retryCount(0)
                    .publishedAt(LocalDateTime.now())
                    .build();
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid analysis task dispatch payload", e);
        }
    }

    private record Destination(String exchange, String routingKey) {
    }
}
