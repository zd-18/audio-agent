package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class NoiseRiskEvaluator {

    private static final double RMS_WEIGHT = 0.15;
    private static final double FLATNESS_WEIGHT = 0.30;
    private static final double ENTROPY_WEIGHT = 0.30;
    private static final double STABILITY_WEIGHT = 0.25;
    private static final double DEGRADED_RMS_WEIGHT = 0.45;
    private static final double DEGRADED_STABILITY_WEIGHT = 0.55;

    private final AnalysisProperties properties;

    public NoiseFrameEvaluation evaluate(NoiseAnalysisFrame frame,
                                         BigDecimal rmsVariationDb) {
        AnalysisProperties.NoiseRisk config = properties.getNoiseRisk();
        BigDecimal rms = frame.rmsDbfs();
        if (rms == null
                || rms.compareTo(config.getMinimumRmsDbfs()) < 0
                || rms.compareTo(config.getMaximumRmsDbfs()) > 0
                || rmsVariationDb == null) {
            return new NoiseFrameEvaluation(false, BigDecimal.ZERO, false);
        }

        double rmsEvidence = rmsEvidence(rms.doubleValue(), config);
        double stabilityEvidence = 1.0 - clamp(
                rmsVariationDb.doubleValue()
                        / config.getMaximumRmsVariationDb().doubleValue());
        BigDecimal flatness = frame.spectralFlatness();
        BigDecimal entropy = frame.spectralEntropy();
        boolean spectralAvailable = flatness != null || entropy != null;
        double score;
        boolean spectralThresholdMet = false;

        if (spectralAvailable) {
            double flatnessEvidence = flatness == null ? 0
                    : thresholdEvidence(flatness.doubleValue(),
                    config.getFlatnessThreshold().doubleValue());
            double entropyEvidence = entropy == null ? 0
                    : thresholdEvidence(entropy.doubleValue(),
                    config.getEntropyThreshold().doubleValue());
            spectralThresholdMet = (flatness != null
                    && flatness.compareTo(config.getFlatnessThreshold()) >= 0)
                    || (entropy != null
                    && entropy.compareTo(config.getEntropyThreshold()) >= 0);
            score = RMS_WEIGHT * rmsEvidence
                    + FLATNESS_WEIGHT * flatnessEvidence
                    + ENTROPY_WEIGHT * entropyEvidence
                    + STABILITY_WEIGHT * stabilityEvidence;
        } else {
            score = DEGRADED_RMS_WEIGHT * rmsEvidence
                    + DEGRADED_STABILITY_WEIGHT * stabilityEvidence;
        }

        BigDecimal roundedScore = BigDecimal.valueOf(clamp(score))
                .setScale(4, RoundingMode.HALF_UP);
        boolean candidate = stabilityEvidence > 0
                && (!spectralAvailable || spectralThresholdMet)
                && roundedScore.compareTo(config.getCandidateScore()) >= 0;
        return new NoiseFrameEvaluation(candidate, roundedScore,
                spectralAvailable);
    }

    public String severity(long durationMs, BigDecimal score) {
        AnalysisProperties.NoiseRisk config = properties.getNoiseRisk();
        if (durationMs >= config.getHighDurationMs()
                || score.compareTo(config.getHighScore()) >= 0) {
            return "HIGH";
        }
        if (durationMs >= config.getMediumDurationMs()
                || score.compareTo(config.getMediumScore()) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double rmsEvidence(double rms,
                               AnalysisProperties.NoiseRisk config) {
        double min = config.getMinimumRmsDbfs().doubleValue();
        double max = config.getMaximumRmsDbfs().doubleValue();
        double midpoint = (min + max) / 2.0;
        double halfRange = (max - min) / 2.0;
        return 1.0 - 0.5 * clamp(Math.abs(rms - midpoint) / halfRange);
    }

    private double thresholdEvidence(double value, double threshold) {
        if (value >= threshold) {
            double remaining = Math.max(1.0e-9, 1.0 - threshold);
            return 0.5 + 0.5 * clamp((value - threshold) / remaining);
        }
        return threshold <= 0 ? 0.5 : 0.5 * clamp(value / threshold);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
