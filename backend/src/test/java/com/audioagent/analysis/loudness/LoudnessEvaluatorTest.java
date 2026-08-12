package com.audioagent.analysis.loudness;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoudnessEvaluatorTest {

    private LoudnessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new LoudnessEvaluator(new AnalysisProperties());
    }

    @Test
    void classifiesLowNormalAndHighFromConfiguredBoundaries() {
        assertEquals("LOW", evaluate("-19.01", "6", "-2")
                .loudnessLevel());
        assertEquals("NORMAL", evaluate("-19.00", "6", "-2")
                .loudnessLevel());
        assertEquals("NORMAL", evaluate("-13.00", "6", "-2")
                .loudnessLevel());
        assertEquals("HIGH", evaluate("-12.99", "6", "-2")
                .loudnessLevel());
    }

    @Test
    void classifiesPeakRiskAndDynamicRange() {
        LoudnessEvaluation narrowRisk = evaluate("-16", "2.99", "-0.9");
        assertEquals("RISK", narrowRisk.peakRisk());
        assertEquals("NARROW", narrowRisk.dynamicRangeLevel());
        assertTrue(narrowRisk.suggestions().size() >= 2);

        assertEquals("NORMAL", evaluate("-16", "3", "-1")
                .dynamicRangeLevel());
        assertEquals("WIDE", evaluate("-16", "18.01", "-2")
                .dynamicRangeLevel());
    }

    private LoudnessEvaluation evaluate(String integrated, String lra,
                                        String truePeak) {
        return evaluator.evaluate(new LoudnessMetrics(
                new BigDecimal(integrated), new BigDecimal(lra),
                new BigDecimal(truePeak), new BigDecimal(truePeak)));
    }
}
