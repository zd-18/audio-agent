package com.audioagent.processing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioProcessingProperties.class)
public class AudioProcessingConfiguration {
}
