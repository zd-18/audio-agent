package com.audioagent.outbox.service;

import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.mapper.OutboxEventMapper;
import com.audioagent.outbox.model.OutboxEventStatus;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.audioagent.outbox.service.impl.OutboxEventServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
}
