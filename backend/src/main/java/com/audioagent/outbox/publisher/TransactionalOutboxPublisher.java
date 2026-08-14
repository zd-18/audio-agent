package com.audioagent.outbox.publisher;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionalOutboxPublisher implements OutboxPublisher {

    private final OutboxPayloadCodec payloadCodec;
    private final OutboxEventService eventService;

    @Override
    @Transactional
    public OutboxEvent publish(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload) {
        return eventService.createPending(
                aggregateType, aggregateId, eventType,
                payloadCodec.serialize(payload));
    }
}
