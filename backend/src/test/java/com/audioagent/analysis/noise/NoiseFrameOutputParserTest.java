package com.audioagent.analysis.noise;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseFrameOutputParserTest {

    private final NoiseFrameOutputParser parser =
            new NoiseFrameOutputParser();

    @Test
    void parsesRmsPeakAndNoiseFloorIncludingNegativeDecimals() {
        List<NoiseAnalysisFrame> frames = parse(
                "frame:0 pts:0 pts_time:0.125",
                "lavfi.astats.Overall.RMS_level=-31.75",
                "lavfi.astats.Overall.Peak_level=-12.25",
                "lavfi.astats.Overall.Noise_floor=-48.5");

        assertEquals(1, frames.size());
        assertEquals(125, frames.getFirst().timestampMs());
        assertEquals(new BigDecimal("-31.75"), frames.getFirst().rmsDbfs());
        assertEquals(new BigDecimal("-12.25"), frames.getFirst().peakDbfs());
        assertEquals(new BigDecimal("-48.5"),
                frames.getFirst().noiseFloorDbfs());
    }

    @Test
    void parsesFlatnessAndAveragesMultipleChannels() {
        NoiseAnalysisFrame frame = parse(
                "frame:0 pts:0 pts_time:0",
                "lavfi.astats.Overall.RMS_level=-30",
                "lavfi.aspectralstats.1.flatness=0.60",
                "lavfi.aspectralstats.2.flatness=0.80").getFirst();

        assertEquals(new BigDecimal("0.70000000"),
                frame.spectralFlatness());
    }

    @Test
    void normalizesSpectralEntropyToZeroOneRange() {
        NoiseAnalysisFrame frame = parse(
                "frame:0 pts:0 pts_time:0",
                "lavfi.astats.Overall.RMS_level=-30",
                "lavfi.aspectralstats.1.entropy=5.0").getFirst();

        assertTrue(frame.spectralEntropy().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(frame.spectralEntropy().compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void ignoresNanAndInfinityWithoutDroppingValidRmsFrame() {
        NoiseAnalysisFrame frame = parse(
                "frame:0 pts:0 pts_time:0",
                "lavfi.astats.Overall.RMS_level=-30",
                "lavfi.astats.Overall.Peak_level=-inf",
                "lavfi.aspectralstats.1.flatness=nan",
                "lavfi.aspectralstats.1.entropy=inf").getFirst();

        assertNull(frame.peakDbfs());
        assertNull(frame.spectralFlatness());
        assertNull(frame.spectralEntropy());
    }

    @Test
    void ignoresIrrelevantLinesAndFramesWithoutRms() {
        List<NoiseAnalysisFrame> frames = parse(
                "unrelated log line",
                "frame:0 pts:0 pts_time:0",
                "lavfi.aspectralstats.1.flatness=0.9",
                "frame:1 pts:100 pts_time:0.1",
                "lavfi.astats.Overall.RMS_level=-32");

        assertEquals(1, frames.size());
        assertEquals(100, frames.getFirst().timestampMs());
    }

    private List<NoiseAnalysisFrame> parse(String... lines) {
        NoiseFrameOutputParser.Accumulator accumulator =
                parser.newAccumulator(2048);
        List.of(lines).forEach(accumulator::accept);
        return accumulator.finish();
    }
}
