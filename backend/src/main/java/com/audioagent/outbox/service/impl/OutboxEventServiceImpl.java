package com.audioagent.outbox.service.impl;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

    private final OutboxEventMapper eventMapper;
    private final OutboxPayloadCodec payloadCodec;

    @Override
    @Transactional
    public OutboxEvent createPending(
            String aggregateType,
            String aggregateId,
            String eventType,
            String jsonPayload) {
        Assert.hasText(aggregateType, "aggregateType must not be blank");
        Assert.hasText(aggregateId, "aggregateId must not be blank");
        Assert.hasText(eventType, "eventType must not be blank");
        String normalizedPayload =
                payloadCodec.validateAndNormalize(jsonPayload);

        OutboxEvent existing = eventMapper.selectByAggregateAndType(
                aggregateType, aggregateId, eventType);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(normalizedPayload);
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        if (eventMapper.insert(event) != 1 || event.getId() == null) {
            throw new IllegalStateException("Failed to insert outbox event");
        }
        return event;
    }
}
