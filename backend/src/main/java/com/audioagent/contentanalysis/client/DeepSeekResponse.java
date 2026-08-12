package com.audioagent.contentanalysis.client;

public record DeepSeekResponse(
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
