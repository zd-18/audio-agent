package com.audioagent.contentanalysis.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio-agent.ai.deepseek")
public class DeepSeekProperties {

    private boolean enabled = true;
    private String apiKey = "";

    @NotBlank
    private String baseUrl = "https://api.deepseek.com";

    @NotBlank
    private String model = "deepseek-v4-flash";

    @Min(1)
    @Max(120)
    private int connectTimeoutSeconds = 10;

    @Min(1)
    @Max(600)
    private int readTimeoutSeconds = 180;

    @Min(1)
    @Max(65536)
    private int maxTokens = 8192;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;

    @Min(4000)
    @Max(1_000_000)
    private int maxInputChars = 60_000;

    @Min(0)
    @Max(10)
    private int maxRetryCount = 2;

    @Min(100)
    @Max(300_000)
    private int retryDelayMilliseconds = 3_000;

    @Min(200)
    @Max(20_000)
    private int maxChunkChars = 3_000;

    @Min(1)
    @Max(32)
    private int maxMapCalls = 8;

    @Min(4_000)
    @Max(1_000_000)
    private int maxOutputChars = 120_000;

    @Min(1)
    @Max(1_000)
    private int maxEvidenceQuoteChars = 100;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
