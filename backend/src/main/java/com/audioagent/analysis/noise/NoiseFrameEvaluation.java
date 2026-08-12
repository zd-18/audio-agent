package com.audioagent.analysis.noise;

import java.math.BigDecimal;

public record NoiseFrameEvaluation(
        boolean candidate,
        BigDecimal score,
        boolean spectralMetricsAvailable
) {
}
