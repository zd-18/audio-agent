package com.audioagent.outbox.worker;

import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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

    @Override
    public CompletableFuture<OutboxBrokerConfirmation> send(
            OutboxEvent event) {
        String eventId = event.getId().toString();
        Message message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
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
                properties.getExchange(), properties.getRoutingKey(),
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
}
