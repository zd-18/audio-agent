package com.audioagent.contentanalysis.config;

import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.HttpAiChatClient;
import com.audioagent.contentanalysis.client.DeepSeekClient;
import com.audioagent.contentanalysis.client.DeepSeekClientAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class ContentAnalysisConfiguration {

    @Bean
    public AiChatClient aiChatClient(DeepSeekProperties properties,
                                     ObjectMapper objectMapper) {
        return new HttpAiChatClient(properties.getBaseUrl(),
                properties::getApiKey, properties::isConfigured,
                objectMapper, Duration.ofSeconds(
                properties.getConnectTimeoutSeconds()));
    }

    @Bean
    public DeepSeekClient deepSeekClient(AiChatClient aiChatClient,
                                         DeepSeekProperties properties) {
        return new DeepSeekClientAdapter(aiChatClient, properties);
    }
}
