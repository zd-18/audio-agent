package com.audioagent.processing.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AudioProcessingExecutionMessageTest {

    @Test
    void messageContainsOnlyExecutionId() throws Exception {
        JsonNode json = new ObjectMapper().readTree(
                new ObjectMapper().writeValueAsString(
                        new AudioProcessingExecutionMessage(90L)));
        assertEquals(1, json.size());
        assertEquals(90L, json.get("executionId").asLong());
        assertFalse(json.has("confirmationJson"));
    }
}
