package com.audioagent.infrastructure.ffprobe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.analysis")
public class AnalysisProperties {

    @Min(1)
    @Max(16)
    private int corePoolSize = 2;

    @Min(1)
    @Max(32)
    private int maxPoolSize = 4;

    @Min(1)
    @Max(1000)
    private int queueCapacity = 100;

    @NotBlank
    private String threadNamePrefix = "audio-analysis-";

    @NotBlank
    private String ffprobePath = "ffprobe";

    @NotBlank
    private String ffmpegPath = "ffmpeg";

    @Min(1)
    @Max(120)
    private int timeoutSeconds = 30;

    @Pattern(regexp = "local|rabbit")
    private String dispatchMode = "local";

    private final Retry retry = new Retry();

    @Valid
    private final Silence silence = new Silence();

    @Valid
    private final Loudness loudness = new Loudness();

    @Valid
    private final VolumeSegment volumeSegment = new VolumeSegment();

    @Valid
    private final NoiseRisk noiseRisk = new NoiseRisk();

    @Valid
    private final Report report = new Report();

    @Valid
    private final ProcessingPlan processingPlan = new ProcessingPlan();

    @Data
    public static class Retry {

        private int maxAttempts = 3;

        private int delayMilliseconds = 10000;
    }

    @Data
    public static class Silence {

        private boolean enabled = true;

        @DecimalMin("-100")
        @DecimalMax("0")
        private BigDecimal noiseThresholdDb = BigDecimal.valueOf(-45);

        @Min(1)
        private long minDurationMs = 1500;

        @Min(1)
        @Max(600)
        private int timeoutSeconds = 60;

        @Min(1)
        private long mediumDurationMs = 3000;

        @Min(1)
        private long highDurationMs = 8000;

        @AssertTrue(message = "silence duration thresholds must satisfy "
                + "min <= medium <= high")
        public boolean isDurationThresholdsValid() {
            return minDurationMs <= mediumDurationMs
                    && mediumDurationMs <= highDurationMs;
        }
    }

    @Data
    public static class Loudness {

        private boolean enabled = true;

        @Min(1)
        @Max(600)
        private int timeoutSeconds = 60;

        @DecimalMin("-100")
        @DecimalMax("20")
        private BigDecimal targetLufs = BigDecimal.valueOf(-16);

        @DecimalMin("0")
        @DecimalMax("50")
        private BigDecimal toleranceLu = BigDecimal.valueOf(3);

        @DecimalMin("-100")
        @DecimalMax("20")
        private BigDecimal truePeakLimitDbfs = BigDecimal.valueOf(-1);

        @DecimalMin("0")
        @DecimalMax("200")
        private BigDecimal lraMinLu = BigDecimal.valueOf(3);

        @DecimalMin("0")
        @DecimalMax("200")
        private BigDecimal lraMaxLu = BigDecimal.valueOf(18);

        @AssertTrue(message = "loudness range thresholds must satisfy "
                + "min <= max")
        public boolean isRangeThresholdsValid() {
            return lraMinLu.compareTo(lraMaxLu) <= 0;
        }
    }

    @Data
    public static class VolumeSegment {

        private boolean enabled = true;

        @DecimalMin("0")
        @DecimalMax("100")
        private BigDecimal lowDeviationLu = BigDecimal.valueOf(8);

        @DecimalMin("0")
        @DecimalMax("100")
        private BigDecimal spikeDeviationLu = BigDecimal.valueOf(6);

        @Min(1)
        private long minDurationMs = 1000;

        @Min(0)
        private long mergeGapMs = 300;

        @DecimalMin("-200")
        @DecimalMax("0")
        private BigDecimal minimumValidLufs = BigDecimal.valueOf(-55);

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal silenceOverlapRatio =
                new BigDecimal("0.5");

        @Min(1)
        private long mediumDurationMs = 3000;

        @Min(1)
        private long highDurationMs = 8000;

        @DecimalMin("0")
        @DecimalMax("100")
        private BigDecimal mediumDeviationLu = BigDecimal.valueOf(10);

        @DecimalMin("0")
        @DecimalMax("100")
        private BigDecimal highDeviationLu = BigDecimal.valueOf(15);

        @AssertTrue(message = "volume segment duration thresholds must "
                + "satisfy min <= medium <= high")
        public boolean isDurationThresholdsValid() {
            return minDurationMs <= mediumDurationMs
                    && mediumDurationMs <= highDurationMs;
        }

        @AssertTrue(message = "volume segment deviation thresholds must "
                + "satisfy medium <= high")
        public boolean isDeviationThresholdsValid() {
            return mediumDeviationLu.compareTo(highDeviationLu) <= 0;
        }
    }

    @Data
    public static class NoiseRisk {

        private boolean enabled = true;

        @Min(1)
        @Max(600)
        private int timeoutSeconds = 60;

        @Min(1)
        @Max(1000)
        private long frameDurationMs = 100;

        @Min(1)
        private long minDurationMs = 1500;

        @Min(0)
        private long mergeGapMs = 400;

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal silenceOverlapRatio = new BigDecimal("0.4");

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal spikeOverlapRatio = new BigDecimal("0.5");

        @DecimalMin("-200")
        @DecimalMax("0")
        private BigDecimal minimumRmsDbfs = BigDecimal.valueOf(-50);

        @DecimalMin("-200")
        @DecimalMax("20")
        private BigDecimal maximumRmsDbfs = BigDecimal.valueOf(-18);

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal flatnessThreshold = new BigDecimal("0.45");

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal entropyThreshold = new BigDecimal("0.70");

        @Min(2)
        @Max(100)
        private int stabilityWindowSize = 8;

        @DecimalMin("0.01")
        @DecimalMax("100")
        private BigDecimal maximumRmsVariationDb = BigDecimal.valueOf(4);

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal candidateScore = new BigDecimal("0.60");

        @Min(1)
        private long mediumDurationMs = 4000;

        @Min(1)
        private long highDurationMs = 10000;

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal mediumScore = new BigDecimal("0.65");

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal highScore = new BigDecimal("0.82");

        @AssertTrue(message = "noise risk RMS thresholds must satisfy min < max")
        public boolean isRmsThresholdsValid() {
            return minimumRmsDbfs.compareTo(maximumRmsDbfs) < 0;
        }

        @AssertTrue(message = "noise risk duration thresholds must satisfy min <= medium <= high")
        public boolean isDurationThresholdsValid() {
            return minDurationMs <= mediumDurationMs
                    && mediumDurationMs <= highDurationMs;
        }

        @AssertTrue(message = "noise risk score thresholds must satisfy candidate <= medium <= high")
        public boolean isScoreThresholdsValid() {
            return candidateScore.compareTo(mediumScore) <= 0
                    && mediumScore.compareTo(highScore) <= 0;
        }
    }

    /**
     * 用户报告及启发式评分配置。所有扣分都集中在这里，便于按产品场景调优；
     * 分数用于产品提示，不代表行业标准或绝对质量结论。
     */
    @Data
    public static class Report {

        private boolean enabled = true;

        @NotBlank
        private String version = "1.0";

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal overlapThresholdRatio = new BigDecimal("0.5");

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal overlapDiscountRatio = new BigDecimal("0.5");

        @Min(1)
        @Max(50)
        private int maxKeyIssues = 5;

        @Min(1)
        @Max(50)
        private int maxRecommendations = 6;

        @Valid
        private final Score score = new Score();

        @Valid
        private final Grade grade = new Grade();
    }

    @Data
    public static class Score {

        @Min(0)
        private int silenceLow = 2;
        @Min(0)
        private int silenceMedium = 5;
        @Min(0)
        private int silenceHigh = 9;
        @Min(0)
        private int volumeDropLow = 3;
        @Min(0)
        private int volumeDropMedium = 6;
        @Min(0)
        private int volumeDropHigh = 10;
        @Min(0)
        private int volumeSpikeLow = 3;
        @Min(0)
        private int volumeSpikeMedium = 7;
        @Min(0)
        private int volumeSpikeHigh = 12;
        @Min(0)
        private int noiseRiskLow = 4;
        @Min(0)
        private int noiseRiskMedium = 8;
        @Min(0)
        private int noiseRiskHigh = 15;
        @Min(0)
        private int loudnessAbnormal = 6;
        @Min(0)
        private int peakRisk = 8;
        @Min(0)
        private int dynamicRangeNarrow = 3;
        @Min(0)
        private int dynamicRangeWide = 4;

        @DecimalMin("0")
        @DecimalMax("1")
        private BigDecimal silenceRatioThreshold = new BigDecimal("0.30");

        @Min(0)
        private int silenceRatioPenalty = 5;
    }

    @Data
    public static class Grade {

        @Min(0)
        @Max(100)
        private int excellentMin = 90;

        @Min(0)
        @Max(100)
        private int goodMin = 75;

        @Min(0)
        @Max(100)
        private int fairMin = 60;

        @AssertTrue(message = "report grade thresholds must satisfy "
                + "0 <= fair <= good <= excellent <= 100")
        public boolean isThresholdsValid() {
            return fairMin <= goodMin && goodMin <= excellentMin;
        }
    }

    @Data
    public static class ProcessingPlan {

        private boolean enabled = true;

        @Min(1)
        private int version = 1;

        @Min(1)
        @Max(200)
        private int maxSteps = 20;

        @Min(1)
        private int manyStepsThreshold = 8;

        @Valid
        private final PlanSilence silence = new PlanSilence();

        @Valid
        private final PlanGain gain = new PlanGain();

        @Valid
        private final PlanDenoise denoise = new PlanDenoise();
    }

    @Data
    public static class PlanSilence {

        private boolean mediumTrimEnabled = true;
        private boolean highTrimEnabled = true;

        @Min(0)
        private long keepHeadMs = 200;

        @Min(0)
        private long keepTailMs = 200;

        @Min(1)
        private long longSilenceMinMs = 3000;

        @Min(100)
        private long keepSilenceMs = 800;
    }

    @Data
    public static class PlanGain {

        @DecimalMin("0.1")
        @DecimalMax("24")
        private BigDecimal increaseDb = BigDecimal.valueOf(3);

        @DecimalMin("-24")
        @DecimalMax("-0.1")
        private BigDecimal decreaseDb = BigDecimal.valueOf(-3);

        @DecimalMin("0.1")
        @DecimalMax("24")
        private BigDecimal maxAbsoluteDb = BigDecimal.valueOf(6);

        @Min(0)
        private long mergeGapMs = 300;

        @AssertTrue(message = "processing plan gain suggestions must not exceed maxAbsoluteDb")
        public boolean isGainRangeValid() {
            return increaseDb != null && decreaseDb != null
                    && maxAbsoluteDb != null
                    && increaseDb.abs().compareTo(maxAbsoluteDb) <= 0
                    && decreaseDb.abs().compareTo(maxAbsoluteDb) <= 0;
        }
    }

    @Data
    public static class PlanDenoise {

        @NotBlank
        private String defaultStrength = "LIGHT";

        @NotBlank
        private String defaultConfidence = "MEDIUM";

        @AssertTrue(message = "noise-risk suggestions must require confirmation")
        private boolean requiresConfirmation = true;
    }
}
