package com.audioagent.ai;

public record AiChatResponse(
        String content,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String model) {
}
