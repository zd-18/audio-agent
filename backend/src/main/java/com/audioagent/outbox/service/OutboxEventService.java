package com.audioagent.outbox.service;

import com.audioagent.outbox.entity.OutboxEvent;

public interface OutboxEventService {

    OutboxEvent createPending(
            String aggregateType,
            String aggregateId,
            String eventType,
            String jsonPayload);

    OutboxEvent retryFailed(Long eventId);
}
