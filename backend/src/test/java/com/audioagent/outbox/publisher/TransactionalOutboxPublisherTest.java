package com.audioagent.outbox.publisher;

import com.audioagent.outbox.service.OutboxEventService;
import com.audioagent.outbox.payload.OutboxPayloadCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TransactionalOutboxPublisherTest {

    private final OutboxEventService service =
            mock(OutboxEventService.class);
    private final TransactionalOutboxPublisher publisher =
            new TransactionalOutboxPublisher(
                    new OutboxPayloadCodec(new ObjectMapper()), service);

    @Test
    void serializesPayloadAsJsonAndOnlyWritesOutboxService() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        publisher.publish("Order", "12", "order.created",
                Map.of("amount", 99, "currency", "CNY"));

        verify(service).createPending(
                eq("Order"), eq("12"), eq("order.created"),
                json.capture());
        assertEquals(Map.of("amount", 99, "currency", "CNY"),
                readMap(json.getValue()));
    }

    @Test
    void rejectsSensitiveFieldsAtAnyNestingLevel() {
        Map<String, Object> payload = Map.of(
                "user", Map.of("access_token", "must-not-persist"));

        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish(
                        "User", "7", "user.changed", payload));
        verifyNoInteractions(service);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
