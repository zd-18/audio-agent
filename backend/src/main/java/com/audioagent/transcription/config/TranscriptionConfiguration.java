package com.audioagent.transcription.config;

import com.audioagent.transcription.client.AsrClient;
import com.audioagent.transcription.client.HttpAsrClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties(TranscriptionProperties.class)
public class TranscriptionConfiguration {

    @Bean
    public AsrClient asrClient(TranscriptionProperties properties,
                               RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(
                properties.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(
                properties.getReadTimeoutSeconds()));
        RestClient restClient = restClientBuilder
                .baseUrl(properties.getAsrBaseUrl())
                .requestFactory(requestFactory)
                .build();

        log.info("ASR client configured, baseUrl={}, connectTimeout={}s, "
                        + "readTimeout={}s",
                properties.getAsrBaseUrl(),
                properties.getConnectTimeoutSeconds(),
                properties.getReadTimeoutSeconds());

        return new HttpAsrClient(restClient);
    }
}
