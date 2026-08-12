package com.audioagent.analysis.loudness;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Ebur128OutputParserTest {

    private final Ebur128OutputParser parser =
            new Ebur128OutputParser();

    @Test
    void parsesAllMetricsFromFinalSummary() {
        LoudnessMetrics metrics = parser.parse(summary(
                "-21.3", "4.6", "-18.1", "-18.0"));

        assertEquals(new BigDecimal("-21.3"),
                metrics.integratedLoudnessLufs());
        assertEquals(new BigDecimal("4.6"), metrics.loudnessRangeLu());
        assertEquals(new BigDecimal("-18.1"), metrics.samplePeakDbfs());
        assertEquals(new BigDecimal("-18.0"), metrics.truePeakDbfs());
    }

    @Test
    void ignoresNoiseAndUsesLastSummary() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            lines.add("frame log " + i + " I: -1.0 LUFS LRA: 99 LU");
        }
        lines.addAll(summary("-30.0", "2.0", "-5.0", "-4.0"));
        lines.add("unrelated output");
        lines.addAll(summary("-16.5", "8.2", "-1.2", "-0.8"));

        LoudnessMetrics metrics = parser.parse(lines);

        assertEquals(new BigDecimal("-16.5"),
                metrics.integratedLoudnessLufs());
        assertEquals(new BigDecimal("8.2"), metrics.loudnessRangeLu());
        assertEquals(new BigDecimal("-1.2"), metrics.samplePeakDbfs());
        assertEquals(new BigDecimal("-0.8"), metrics.truePeakDbfs());
    }

    @Test
    void allowsMissingOrNonFiniteOptionalPeak() {
        List<String> lines = List.of(
                "[Parsed_ebur128] Summary:",
                "  Integrated loudness:",
                "    I:         -42.75 LUFS",
                "  Loudness range:",
                "    LRA:         0.0 LU",
                "  Sample peak:",
                "    Peak:       -inf dBFS");

        LoudnessMetrics metrics = parser.parse(lines);

        assertEquals(new BigDecimal("-42.75"),
                metrics.integratedLoudnessLufs());
        assertNull(metrics.samplePeakDbfs());
        assertNull(metrics.truePeakDbfs());
    }

    @Test
    void failsWhenIntegratedLoudnessIsMissingOrInfinite() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(List.of(
                        "Summary:", "  Integrated loudness:",
                        "    I: -inf LUFS")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse(List.of(
                        "Summary:", "  Loudness range:",
                        "    LRA: 3.0 LU")));
    }

    @Test
    void incrementallyParsesFramesInfinityAndFinalSummary() {
        Ebur128OutputParser.Accumulator accumulator =
                parser.newAccumulator();
        List.of(
                "unrelated log",
                "[Parsed_ametadata] frame:0 pts:0 pts_time:0",
                "[Parsed_ametadata] lavfi.r128.M=-23.75",
                "[Parsed_ametadata] lavfi.r128.S=-24.50",
                "[Parsed_ametadata] broken=value",
                "[Parsed_ametadata] frame:1 pts:4410 pts_time:0.1",
                "[Parsed_ametadata] lavfi.r128.M=-inf",
                "[Parsed_ametadata] lavfi.r128.S=-25.25"
        ).forEach(accumulator::accept);
        summary("-21.3", "4.6", "-18.1", "-18.0")
                .forEach(accumulator::accept);

        LoudnessAnalysis analysis = accumulator.finish();

        assertEquals(2, analysis.frames().size());
        assertEquals(0, analysis.frames().get(0).timestampMs());
        assertEquals(100, analysis.frames().get(1).timestampMs());
        assertEquals(new BigDecimal("-23.75"),
                analysis.frames().get(0).momentaryLufs());
        assertNull(analysis.frames().get(1).momentaryLufs());
        assertEquals(new BigDecimal("-25.25"),
                analysis.frames().get(1).shortTermLufs());
        assertEquals(new BigDecimal("-21.3"),
                analysis.metrics().integratedLoudnessLufs());
    }

    @Test
    void largeUnrelatedOutputIsNotRetainedAsFrames() {
        Ebur128OutputParser.Accumulator accumulator =
                parser.newAccumulator();
        for (int i = 0; i < 100_000; i++) {
            accumulator.accept("unrelated line " + i);
        }
        summary("-20.0", "5.0", "-2.0", "-1.5")
                .forEach(accumulator::accept);

        LoudnessAnalysis analysis = accumulator.finish();

        assertEquals(0, analysis.frames().size());
        assertEquals(new BigDecimal("-20.0"),
                analysis.metrics().integratedLoudnessLufs());
    }

    private List<String> summary(String integrated, String lra,
                                 String samplePeak, String truePeak) {
        return List.of(
                "[Parsed_ebur128_0] Summary:",
                "  Integrated loudness:",
                "    I:         " + integrated + " LUFS",
                "    Threshold: -31.4 LUFS",
                "  Loudness range:",
                "    LRA:         " + lra + " LU",
                "  Sample peak:",
                "    Peak:      " + samplePeak + " dBFS",
                "  True peak:",
                "    Peak:      " + truePeak + " dBFS");
    }
}
