package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseRiskOverlapFilterTest {

    private final NoiseRiskOverlapFilter filter =
            new NoiseRiskOverlapFilter(new AnalysisProperties());

    @Test
    void silenceAtConfiguredRatioFiltersCandidate() {
        assertTrue(filter.filter(List.of(segment()),
                List.of(new NoiseOverlapRange(0, 800)), List.of()).isEmpty());
    }

    @Test
    void smallSilenceOverlapKeepsCandidate() {
        assertEquals(1, filter.filter(List.of(segment()),
                List.of(new NoiseOverlapRange(0, 799)), List.of()).size());
    }

    @Test
    void largeSpikeOverlapFiltersCandidate() {
        assertTrue(filter.filter(List.of(segment()), List.of(),
                List.of(new NoiseOverlapRange(500, 1_500))).isEmpty());
    }

    @Test
    void emptyExclusionListsAreSafe() {
        assertEquals(1, filter.filter(List.of(segment()),
                List.of(), List.of()).size());
    }

    private NoiseRiskSegment segment() {
        return new NoiseRiskSegment(0, 2_000, 2_000,
                new BigDecimal("-32"), new BigDecimal("0.8"),
                new BigDecimal("0.8"), new BigDecimal("1.0"),
                new BigDecimal("0.75"), new BigDecimal("0.8"),
                "HIGH", 20, "MEDIUM");
    }
}
