package com.audioagent.outbox.service.impl;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.OutboxEventService;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
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

    @Override
    @Transactional
    public OutboxEvent retryFailed(Long eventId) {
        Assert.notNull(eventId, "eventId must not be null");
        OutboxEvent current = eventMapper.selectById(eventId);
        validateRetryable(current);

        LocalDateTime now = LocalDateTime.now();
        if (eventMapper.retryFailed(eventId, now) != 1) {
            OutboxEvent latest = eventMapper.selectById(eventId);
            validateRetryable(latest);
            throw new BusinessException(
                    ErrorCode.OUTBOX_EVENT_NOT_RETRYABLE,
                    "Outbox event state changed while retrying");
        }

        log.warn("Outbox FAILED event manually reset for delivery, "
                        + "eventId={}, previousRetryCount={}",
                eventId, current.getRetryCount());
        OutboxEvent retried = eventMapper.selectById(eventId);
        if (retried == null) {
            throw new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND);
        }
        return retried;
    }

    private void validateRetryable(OutboxEvent event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND);
        }
        if (event.getStatus() != OutboxEventStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.OUTBOX_EVENT_NOT_RETRYABLE,
                    "Only FAILED outbox events can be retried; current status="
                            + event.getStatus());
        }
    }
}
