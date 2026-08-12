package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseRiskAnalyzerTest {

    private AnalysisProperties properties;
    private NoiseRiskEvaluator evaluator;
    private NoiseRiskAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        properties = new AnalysisProperties();
        evaluator = new NoiseRiskEvaluator(properties);
        analyzer = new NoiseRiskAnalyzer(properties, evaluator,
                new NoiseRiskSegmentMerger(properties, evaluator));
    }

    @Test
    void broadbandStableFrameGetsHigherRiskScore() {
        NoiseFrameEvaluation high = evaluator.evaluate(
                frame(0, "-32", "0.85", "0.85"),
                new BigDecimal("0.5"));
        NoiseFrameEvaluation low = evaluator.evaluate(
                frame(0, "-32", "0.10", "0.30"),
                new BigDecimal("3.5"));

        assertTrue(high.candidate());
        assertTrue(high.score().compareTo(low.score()) > 0);
        assertTrue(!low.candidate());
    }

    @Test
    void singleFrameNeverProducesSegment() {
        assertTrue(analyzer.analyze(List.of(
                frame(0, "-32", "0.85", "0.85")), 10_000).isEmpty());
    }

    @Test
    void continuousBroadbandFramesMergeIntoOneSegment() {
        List<NoiseRiskSegment> segments = analyzer.analyze(
                frames(0, 21, 100), 2_100);

        assertEquals(1, segments.size());
        assertEquals(100, segments.getFirst().startMs());
        assertEquals(2_100, segments.getFirst().endMs());
        assertEquals(20, segments.getFirst().sampleCount());
    }

    @Test
    void shortCandidateSequenceIsFiltered() {
        assertTrue(analyzer.analyze(frames(0, 10, 100), 1_000).isEmpty());
    }

    @Test
    void smallGapIsMergedAndLargeGapIsSeparated() {
        properties.getNoiseRisk().setMinDurationMs(300);
        properties.getNoiseRisk().setMergeGapMs(200);
        List<NoiseAnalysisFrame> input = new ArrayList<>();
        input.addAll(frames(0, 6, 100));
        input.addAll(frames(700, 6, 100));
        input.addAll(frames(2_000, 6, 100));

        List<NoiseRiskSegment> segments = analyzer.analyze(input, 3_000);

        assertEquals(2, segments.size());
        assertEquals(1_300, segments.getFirst().endMs());
        assertEquals(2_000, segments.get(1).startMs());
    }

    @Test
    void severityUsesConfiguredDurationAndScoreThresholds() {
        assertEquals("HIGH", evaluator.severity(10_000,
                new BigDecimal("0.20")));
        assertEquals("HIGH", evaluator.severity(2_000,
                new BigDecimal("0.82")));
        assertEquals("MEDIUM", evaluator.severity(4_000,
                new BigDecimal("0.20")));
        assertEquals("LOW", evaluator.severity(2_000,
                new BigDecimal("0.20")));
    }

    @Test
    void missingSpectralMetricsUseDegradedLowConfidenceRule() {
        properties.getNoiseRisk().setCandidateScore(new BigDecimal("0.50"));
        List<NoiseAnalysisFrame> input = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            input.add(frame(i * 100L, "-32", null, null));
        }

        List<NoiseRiskSegment> segments = analyzer.analyze(input, 2_000);

        assertEquals(1, segments.size());
        assertEquals("LOW", segments.getFirst().confidence());
        assertEquals(null, segments.getFirst().averageSpectralFlatness());
    }

    private List<NoiseAnalysisFrame> frames(long startMs, int count,
                                            long intervalMs) {
        List<NoiseAnalysisFrame> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String rms = i % 2 == 0 ? "-32.0" : "-32.2";
            frames.add(frame(startMs + i * intervalMs, rms,
                    "0.85", "0.85"));
        }
        return frames;
    }

    private NoiseAnalysisFrame frame(long timestampMs, String rms,
                                     String flatness, String entropy) {
        return new NoiseAnalysisFrame(timestampMs,
                decimal(rms), new BigDecimal("-20"),
                new BigDecimal("-45"), decimal(flatness),
                decimal(entropy), new BigDecimal("8000"));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
