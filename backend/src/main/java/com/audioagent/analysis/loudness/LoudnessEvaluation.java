package com.audioagent.analysis.loudness;

import java.util.List;

public record LoudnessEvaluation(
        String loudnessLevel,
        String peakRisk,
        String dynamicRangeLevel,
        List<String> suggestions
) {
}
