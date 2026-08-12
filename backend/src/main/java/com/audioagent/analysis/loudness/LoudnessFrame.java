package com.audioagent.analysis.loudness;

import java.math.BigDecimal;

public record LoudnessFrame(
        long timestampMs,
        BigDecimal momentaryLufs,
        BigDecimal shortTermLufs
) {
}
