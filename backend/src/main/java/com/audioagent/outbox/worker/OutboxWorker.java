package com.audioagent.outbox.worker;

import com.audioagent.outbox.config.OutboxProperties;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxWorker {

    private static final int LAST_ERROR_LIMIT = 1_000;

    private final OutboxEventMapper eventMapper;
    private final OutboxMessageSender messageSender;
    private final OutboxProperties properties;
    private final String lockOwner;

    @Autowired
    public OutboxWorker(
            OutboxEventMapper eventMapper,
            OutboxMessageSender messageSender,
            OutboxProperties properties) {
        this(eventMapper, messageSender, properties,
                UUID.randomUUID().toString());
    }

    OutboxWorker(
            OutboxEventMapper eventMapper,
            OutboxMessageSender messageSender,
            OutboxProperties properties,
            String lockOwner) {
        this.eventMapper = eventMapper;
        this.messageSender = messageSender;
        this.properties = properties;
        this.lockOwner = lockOwner;
    }

    @Scheduled(fixedDelayString = "${audio.outbox.scan-interval-ms:1000}")
    public void scanAndPublish() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusNanos(
                TimeUnit.MILLISECONDS.toNanos(
                        properties.getLockTimeoutMs()));
        List<Long> ids = eventMapper.selectClaimableIds(
                now, staleBefore, properties.getBatchSize());
        for (Long id : ids) {
            publishIfClaimed(id, now, staleBefore);
        }
    }

    private void publishIfClaimed(
            Long id,
            LocalDateTime now,
            LocalDateTime staleBefore) {
        if (eventMapper.claim(id, lockOwner, now, staleBefore) != 1) {
            return;
        }
        OutboxEvent event = eventMapper.selectById(id);
        if (event == null) {
            log.error("Claimed outbox event disappeared, eventId={}", id);
            return;
        }

        try {
            messageSender.send(event)
                    .orTimeout(properties.getConfirmTimeoutMs(),
                            TimeUnit.MILLISECONDS)
                    .whenComplete((confirmation, failure) -> {
                        if (failure != null) {
                            recordFailure(event,
                                    rootMessage(failure));
                        } else if (!confirmation.acknowledged()) {
                            recordFailure(event, confirmation.reason());
                        } else {
                            markPublished(event);
                        }
                    });
        } catch (RuntimeException e) {
            recordFailure(event, rootMessage(e));
        }
    }

    private void markPublished(OutboxEvent event) {
        int updated = eventMapper.markPublished(
                event.getId(), lockOwner, LocalDateTime.now());
        if (updated == 1) {
            log.debug("Outbox event published, eventId={}", event.getId());
        } else {
            log.warn("Ignored late outbox confirm, eventId={}",
                    event.getId());
        }
    }

    private void recordFailure(OutboxEvent event, String reason) {
        int nextRetryCount = event.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plusNanos(
                TimeUnit.MILLISECONDS.toNanos(
                        retryDelayMs(nextRetryCount)));
        String safeReason = truncate(reason == null
                ? "RabbitMQ publish failed without a reason" : reason);
        int updated = eventMapper.recordFailure(
                event.getId(), lockOwner, nextRetryCount,
                properties.getMaxRetryCount(), nextRetryAt,
                safeReason, now);
        if (updated == 1) {
            log.warn("Outbox publish failed, eventId={}, attempt={}/{}, "
                            + "reason={}",
                    event.getId(), nextRetryCount,
                    properties.getMaxRetryCount(), safeReason);
        } else {
            log.warn("Ignored late outbox failure, eventId={}",
                    event.getId());
        }
    }

    private long retryDelayMs(int failedAttempt) {
        int exponent = Math.min(Math.max(0, failedAttempt - 1), 30);
        long multiplier = 1L << exponent;
        long initial = properties.getInitialRetryDelayMs();
        long maximum = properties.getMaxRetryDelayMs();
        if (initial >= maximum / multiplier) {
            return maximum;
        }
        return Math.min(initial * multiplier, maximum);
    }

    private String truncate(String value) {
        if (value.length() <= LAST_ERROR_LIMIT) {
            return value;
        }
        return value.substring(0, LAST_ERROR_LIMIT);
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }
}
