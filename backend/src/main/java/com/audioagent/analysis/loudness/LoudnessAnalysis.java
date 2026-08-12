package com.audioagent.analysis.loudness;

import com.audioagent.analysis.noise.NoiseAnalysisFrame;

import java.util.List;

public record LoudnessAnalysis(
        LoudnessMetrics metrics,
        List<LoudnessFrame> frames,
        List<NoiseAnalysisFrame> noiseFrames
) {
    public LoudnessAnalysis(LoudnessMetrics metrics,
                            List<LoudnessFrame> frames) {
        this(metrics, frames, List.of());
    }
}
