package com.audioagent.analysis.outbox;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.service.OutboxEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioAnalysisTaskDispatchOutboxService {

    private final OutboxEventService outboxEventService;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent createInitialDispatch(Long taskId) {
        OutboxEvent event = outboxEventService.createPending(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE,
                taskId.toString(),
                AudioAnalysisTaskDispatchEvent.EVENT_TYPE,
                payload(taskId));
        log.info("Analysis task dispatch outbox ensured, taskId={}, "
                        + "outboxEventId={}, status={}",
                taskId, event.getId(), event.getStatus());
        return event;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent reactivateForManualRetry(Long taskId) {
        OutboxEvent existing = outboxEventMapper.selectByAggregateAndType(
                AudioAnalysisTaskDispatchEvent.AGGREGATE_TYPE,
                taskId.toString(),
                AudioAnalysisTaskDispatchEvent.EVENT_TYPE);
        if (existing == null) {
            return createInitialDispatch(taskId);
        }
        if (existing.getStatus() != OutboxEventStatus.PUBLISHED
                && existing.getStatus() != OutboxEventStatus.FAILED) {
            throw new IllegalStateException(
                    "Analysis dispatch outbox cannot be reactivated from "
                            + existing.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = outboxEventMapper.reactivate(
                existing.getId(), existing.getStatus(), now);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Analysis dispatch outbox state changed during retry");
        }
        log.info("Analysis dispatch outbox reactivated for manual retry, "
                        + "taskId={}, outboxEventId={}, previousStatus={}",
                taskId, existing.getId(), existing.getStatus());
        OutboxEvent reactivated = outboxEventMapper.selectById(
                existing.getId());
        if (reactivated == null
                || reactivated.getStatus() != OutboxEventStatus.PENDING) {
            throw new IllegalStateException(
                    "Reactivated analysis dispatch outbox is unavailable");
        }
        return reactivated;
    }

    private String payload(Long taskId) {
        try {
            return objectMapper.writeValueAsString(
                    new AudioAnalysisTaskDispatchEvent.Payload(taskId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize analysis dispatch event", e);
        }
    }
}
