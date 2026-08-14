package com.audioagent.outbox.publisher;

import com.audioagent.outbox.entity.OutboxEvent;

public interface OutboxPublisher {

    OutboxEvent publish(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload);
}
