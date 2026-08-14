package com.audioagent.outbox.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @Min(1)
    private int maxRetryCount = 5;

    @Min(100)
    private long scanIntervalMs = 1_000;

    @Min(100)
    private long initialRetryDelayMs = 2_000;

    @Min(100)
    private long maxRetryDelayMs = 300_000;

    @Min(1_000)
    private long confirmTimeoutMs = 10_000;

    @Min(1_000)
    private long lockTimeoutMs = 60_000;

    @NotBlank
    private String exchange = "audio-agent.events";

    @NotBlank
    private String routingKey = "outbox.event";

    @NotBlank
    private String queue = "audio-agent.outbox.events";
}
