package com.audioagent.contentanalysis.client;

public interface DeepSeekClient {

    DeepSeekResponse complete(String systemPrompt, String userPrompt);
}
