package com.audioagent.outbox.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OutboxPayloadCodec {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "accesstoken",
            "refreshtoken", "authorization", "apikey", "secret",
            "clientsecret", "credentials");

    private final ObjectMapper objectMapper;

    public String serialize(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return serializeNode(objectMapper.valueToTree(payload));
    }

    public String validateAndNormalize(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            throw new IllegalArgumentException(
                    "jsonPayload must not be blank");
        }
        try {
            return serializeNode(objectMapper.readTree(jsonPayload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "outbox payload must be valid JSON", e);
        }
    }

    private String serializeNode(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(
                    "outbox payload must be a JSON object");
        }
        rejectSensitiveFields(json);
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "outbox payload cannot be serialized as JSON", e);
        }
    }

    private void rejectSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String normalized = name.replace("_", "")
                        .replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (SENSITIVE_KEYS.contains(normalized)) {
                    throw new IllegalArgumentException(
                            "sensitive field is forbidden in outbox payload: "
                                    + name);
                }
                rejectSensitiveFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::rejectSensitiveFields);
        }
    }
}
