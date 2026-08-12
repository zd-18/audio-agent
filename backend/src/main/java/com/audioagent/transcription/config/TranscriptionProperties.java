package com.audioagent.transcription.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.transcription")
public class TranscriptionProperties {

    private boolean enabled = true;

    @NotBlank
    private String asrBaseUrl = "http://127.0.0.1:8090";

    @Min(1)
    @Max(120)
    private int connectTimeoutSeconds = 10;

    @Min(1)
    @Max(3600)
    private int readTimeoutSeconds = 600;

    @Pattern(regexp = "[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})?")
    private String language = "zh";

    private boolean speakerDiarization = false;

    @Min(0)
    @Max(20)
    private int maxRetryCount = 3;

    @Min(100)
    @Max(3_600_000)
    private int retryDelayMilliseconds = 10_000;

    @Min(1)
    @Max(3600)
    private int ffmpegTimeoutSeconds = 600;

    @NotBlank
    private String tempRoot = System.getProperty("java.io.tmpdir")
            + "/audio-agent/transcriptions";

    @NotBlank
    private String provider = "funasr";

    @NotBlank
    private String modelName = "paraformer-zh";

    @Min(1)
    @Max(100_000)
    private int maxSegmentCount = 20_000;
}
