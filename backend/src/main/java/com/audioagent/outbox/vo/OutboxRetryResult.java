package com.audioagent.outbox.vo;

import com.audioagent.outbox.entity.OutboxEvent;

import java.time.LocalDateTime;

/**
 * Internal operation response. Deliberately excludes the event payload and
 * error text so the retry endpoint cannot expose message contents.
 */
public record OutboxRetryResult(
        Long eventId,
        String status,
        Integer retryCount,
        LocalDateTime nextRetryAt) {

    public static OutboxRetryResult from(OutboxEvent event) {
        return new OutboxRetryResult(
                event.getId(), event.getStatus().name(),
                event.getRetryCount(), event.getNextRetryAt());
    }
}
