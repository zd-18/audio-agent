package com.audioagent.agent.validation;

import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.agent.model.AgentModelResponse;
import com.audioagent.common.enums.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentResponseParser {

    private final ObjectMapper objectMapper;

    public AgentModelResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Agent returned an empty response", null);
        }
        String json = stripFence(raw.trim());
        try {
            AgentModelResponse response = objectMapper.readValue(
                    json, AgentModelResponse.class);
            if (response.getAnswer() == null
                    || response.getAnswer().isBlank()
                    || response.getInsufficientContext() == null
                    || response.getCitations() == null
                    || response.getCitations().size() > 5) {
                throw invalid("Agent response is incomplete", null);
            }
            return response;
        } catch (JsonProcessingException e) {
            throw invalid("Agent response is not valid JSON", e);
        }
    }

    private String stripFence(String raw) {
        if (!raw.startsWith("```")) {
            return raw;
        }
        int firstNewline = raw.indexOf('\n');
        int lastFence = raw.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return raw;
        }
        return raw.substring(firstNewline + 1, lastFence).trim();
    }

    private AgentExecutionException invalid(String message,
                                            Throwable cause) {
        return new AgentExecutionException(
                ErrorCode.AGENT_RESPONSE_INVALID, false, message, cause);
    }
}
