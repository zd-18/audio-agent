package com.audioagent.analysis.loudness;

import java.math.BigDecimal;

public record LoudnessMetrics(
        BigDecimal integratedLoudnessLufs,
        BigDecimal loudnessRangeLu,
        BigDecimal samplePeakDbfs,
        BigDecimal truePeakDbfs
) {
}
