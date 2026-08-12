package com.audioagent.analysis.noise;

import java.math.BigDecimal;

public record NoiseAnalysisFrame(
        long timestampMs,
        BigDecimal rmsDbfs,
        BigDecimal peakDbfs,
        BigDecimal noiseFloorDbfs,
        BigDecimal spectralFlatness,
        BigDecimal spectralEntropy,
        BigDecimal spectralCentroid
) {
}
