package com.audioagent.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record AiChatRequest(
        String model,
        List<AiChatMessage> messages,
        double temperature,
        int maxTokens,
        Map<String, String> responseFormat,
        Duration timeout) {
}
