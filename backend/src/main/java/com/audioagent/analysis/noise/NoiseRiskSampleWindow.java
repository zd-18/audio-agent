package com.audioagent.analysis.noise;

import java.math.BigDecimal;

record NoiseRiskSampleWindow(
        long startMs,
        long endMs,
        NoiseAnalysisFrame frame,
        BigDecimal score,
        boolean spectralMetricsAvailable
) {
}
