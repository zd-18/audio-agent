package com.audioagent.outbox.service;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.impl.OutboxEventServiceImpl;
import com.audioagent.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceImplTest {

    private final OutboxEventMapper mapper = mock(OutboxEventMapper.class);
    private final OutboxEventService service = new OutboxEventServiceImpl(
            mapper, new OutboxPayloadCodec(new ObjectMapper()));

    @Test
    void createsPendingEventWithNormalizedJson() {
        ArgumentCaptor<OutboxEvent> saved =
                ArgumentCaptor.forClass(OutboxEvent.class);
        doAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            event.setId(91L);
            return 1;
        }).when(mapper).insert(
                org.mockito.ArgumentMatchers.any(OutboxEvent.class));

        service.createPending(
                "Order", "99", "order.created", "{ \"ok\" : true }");

        verify(mapper).insert(saved.capture());
        OutboxEvent event = saved.getValue();
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertEquals("{\"ok\":true}", event.getPayload());
        assertTrue(event.getCreatedAt().equals(event.getUpdatedAt()));
    }

    @Test
    void serviceAlsoRejectsSensitiveJsonWhenCalledDirectly() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createPending(
                        "User", "7", "user.changed",
                        "{\"password\":\"must-not-persist\"}"));

        verify(mapper, never()).insert(
                org.mockito.ArgumentMatchers.any(OutboxEvent.class));
    }

    @Test
    void returnsExistingEventForSameAggregateAndType() {
        OutboxEvent existing = new OutboxEvent();
        existing.setId(73L);
        org.mockito.Mockito.when(mapper.selectByAggregateAndType(
                        "AudioFile", "19", "AUDIO_FILE_UPLOADED"))
                .thenReturn(existing);

        OutboxEvent result = service.createPending(
                "AudioFile", "19", "AUDIO_FILE_UPLOADED",
                "{\"audioFileId\":19}");

        assertEquals(73L, result.getId());
        verify(mapper, never()).insert(
                org.mockito.ArgumentMatchers.any(OutboxEvent.class));
    }

    @Test
    void failedEventCanBeResetAsANewManualRetryCycle() {
        OutboxEvent failed = event(91L, OutboxEventStatus.FAILED);
        failed.setRetryCount(5);
        failed.setLockOwner("worker-1");
        failed.setLockedAt(LocalDateTime.now().minusMinutes(1));
        failed.setLastError("broker unavailable");
        OutboxEvent pending = event(91L, OutboxEventStatus.PENDING);
        pending.setRetryCount(0);
        when(mapper.selectById(91L)).thenReturn(failed, pending);
        when(mapper.retryFailed(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1);

        OutboxEvent result = service.retryFailed(91L);

        assertEquals(OutboxEventStatus.PENDING, result.getStatus());
        assertEquals(0, result.getRetryCount());
        assertNull(result.getLockOwner());
        assertNull(result.getLockedAt());
        assertNull(result.getLastError());
        verify(mapper).retryFailed(
                org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void publishedEventCannotBeManuallyRetried() {
        when(mapper.selectById(92L)).thenReturn(
                event(92L, OutboxEventStatus.PUBLISHED));

        assertThrows(BusinessException.class,
                () -> service.retryFailed(92L));

        verify(mapper, never()).retryFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void processingEventCannotBeManuallyRetried() {
        when(mapper.selectById(93L)).thenReturn(
                event(93L, OutboxEventStatus.PROCESSING));

        assertThrows(BusinessException.class,
                () -> service.retryFailed(93L));

        verify(mapper, never()).retryFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void pendingEventRepeatedRetryRequestIsExplicitlyRejected() {
        when(mapper.selectById(94L)).thenReturn(
                event(94L, OutboxEventStatus.PENDING));

        assertThrows(BusinessException.class,
                () -> service.retryFailed(94L));

        verify(mapper, never()).retryFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    private OutboxEvent event(Long id, OutboxEventStatus status) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setStatus(status);
        return event;
    }
}
