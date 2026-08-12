package com.audioagent.processing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.processing")
public class AudioProcessingProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(20)
    private int maxRetryCount = 3;

    @Min(1)
    @Max(3600)
    private int executionTimeoutSeconds = 600;

    @Min(100)
    @Max(3_600_000)
    private int retryDelayMilliseconds = 10_000;

    @NotBlank
    private String tempRoot = System.getProperty("java.io.tmpdir")
            + "/audio-agent/executions";

    @Valid
    private final Output output = new Output();

    @Valid
    private final Denoise denoise = new Denoise();

    @Valid
    private final Loudness loudness = new Loudness();

    @Valid
    private final Validation validation = new Validation();

    @Data
    public static class Output {
        @Pattern(regexp = "wav")
        private String format = "wav";

        @Pattern(regexp = "pcm_s16le")
        private String codec = "pcm_s16le";

        @Min(8_000)
        @Max(192_000)
        private int sampleRate = 48_000;

        @Pattern(regexp = "preserve|mono|stereo")
        private String channels = "preserve";
    }

    @Data
    public static class Denoise {
        @DecimalMin("-80")
        @DecimalMax("-20")
        private BigDecimal lightNoiseFloorDb = BigDecimal.valueOf(-35);

        @DecimalMin("-80")
        @DecimalMax("-20")
        private BigDecimal mediumNoiseFloorDb = BigDecimal.valueOf(-30);

        @AssertTrue(message = "medium denoise must not be weaker than light denoise")
        public boolean isStrengthOrderValid() {
            return lightNoiseFloorDb != null && mediumNoiseFloorDb != null
                    && lightNoiseFloorDb.compareTo(mediumNoiseFloorDb) <= 0;
        }
    }

    @Data
    public static class Loudness {
        @DecimalMin("1")
        @DecimalMax("50")
        private BigDecimal targetLra = BigDecimal.valueOf(11);
    }

    @Data
    public static class Validation {
        @Min(1)
        private long minimumOutputDurationMs = 500;

        @Min(1)
        private long minimumOutputSizeBytes = 1024;

        @Min(0)
        private long durationToleranceMs = 1000;
    }
}
