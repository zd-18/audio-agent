package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NoiseRiskOverlapFilter {

    private final AnalysisProperties properties;

    public List<NoiseRiskSegment> filter(List<NoiseRiskSegment> candidates,
                                         List<NoiseOverlapRange> silence,
                                         List<NoiseOverlapRange> spikes) {
        AnalysisProperties.NoiseRisk config = properties.getNoiseRisk();
        return candidates.stream()
                .filter(segment -> overlapRatio(segment, silence)
                        .compareTo(config.getSilenceOverlapRatio()) < 0)
                .filter(segment -> overlapRatio(segment, spikes)
                        .compareTo(config.getSpikeOverlapRatio()) < 0)
                .toList();
    }

    private BigDecimal overlapRatio(NoiseRiskSegment segment,
                                    List<NoiseOverlapRange> ranges) {
        if (segment.durationMs() <= 0 || ranges == null || ranges.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long overlap = ranges.stream().mapToLong(range -> Math.max(0,
                Math.min(segment.endMs(), range.endMs())
                        - Math.max(segment.startMs(), range.startMs())))
                .sum();
        overlap = Math.min(overlap, segment.durationMs());
        return BigDecimal.valueOf(overlap).divide(
                BigDecimal.valueOf(segment.durationMs()), 8,
                RoundingMode.HALF_UP);
    }
}
