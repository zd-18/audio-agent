package com.audioagent.agent.validation;

import com.audioagent.agent.exception.AgentExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentResponseParserTest {

    private final AgentResponseParser parser =
            new AgentResponseParser(new ObjectMapper());

    @Test
    void parsesLightweightJsonInsideMarkdownFence() {
        var result = parser.parse("""
                ```json
                {"answer":"回答","insufficientContext":false,
                 "citations":[{"segmentId":"301","quote":"原文"}]}
                ```
                """);

        assertEquals("回答", result.getAnswer());
        assertEquals("301", result.getCitations().getFirst().getSegmentId());
    }

    @Test
    void rejectsMissingRequiredFieldsAndTooManyCitations() {
        assertThrows(AgentExecutionException.class,
                () -> parser.parse("{\"answer\":\"x\"}"));
        assertThrows(AgentExecutionException.class,
                () -> parser.parse("""
                        {"answer":"x","insufficientContext":false,
                        "citations":[{},{},{},{},{},{}]}
                        """));
    }
}
