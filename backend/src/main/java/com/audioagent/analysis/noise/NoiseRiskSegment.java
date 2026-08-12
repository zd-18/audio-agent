package com.audioagent.analysis.noise;

import java.math.BigDecimal;

public record NoiseRiskSegment(
        long startMs,
        long endMs,
        long durationMs,
        BigDecimal averageRmsDbfs,
        BigDecimal averageSpectralFlatness,
        BigDecimal averageSpectralEntropy,
        BigDecimal rmsVariationDb,
        BigDecimal noiseRiskScore,
        BigDecimal maximumNoiseRiskScore,
        String confidence,
        int sampleCount,
        String severity
) {
}
