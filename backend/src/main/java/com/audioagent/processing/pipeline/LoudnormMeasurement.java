package com.audioagent.processing.pipeline;

import java.math.BigDecimal;

public record LoudnormMeasurement(
        BigDecimal inputI,
        BigDecimal inputTp,
        BigDecimal inputLra,
        BigDecimal inputThresh,
        BigDecimal targetOffset
) {
}
