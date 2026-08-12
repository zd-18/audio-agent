package com.audioagent.analysis.volume;

import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VolumeIssueSilenceFilterTest {

    private VolumeIssueSilenceFilter filter;

    @BeforeEach
    void setUp() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.getVolumeSegment().setMinDurationMs(500);
        filter = new VolumeIssueSilenceFilter(properties);
    }

    @Test
    void removesDropWhenSilenceOverlapReachesThreshold() {
        List<VolumeIssueSegment> result = filter.excludeSilence(
                List.of(drop(0, 4_000)),
                List.of(new SilenceSegment(0, 2_500, 2_500)),
                5_000);

        assertEquals(0, result.size());
    }

    @Test
    void trimsPartialSilenceIntoNonSilentPieces() {
        List<VolumeIssueSegment> result = filter.excludeSilence(
                List.of(drop(0, 4_000)),
                List.of(new SilenceSegment(1_000, 1_500, 500)),
                5_000);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).startMs());
        assertEquals(1_000, result.get(0).endMs());
        assertEquals(1_500, result.get(1).startMs());
        assertEquals(4_000, result.get(1).endMs());
    }

    @Test
    void emptySilenceListReturnsCandidatesNormally() {
        VolumeIssueSegment candidate = drop(0, 4_000);
        List<VolumeIssueSegment> result = filter.excludeSilence(
                List.of(candidate), List.of(), 5_000);

        assertSame(candidate, result.getFirst());
    }

    private VolumeIssueSegment drop(long start, long end) {
        List<VolumeSampleWindow> samples = LongStream.iterate(start,
                        value -> value < end, value -> value + 1_000)
                .mapToObj(value -> new VolumeSampleWindow(
                        VolumeIssueType.VOLUME_DROP, value,
                        Math.min(value + 1_000, end),
                        new BigDecimal("-30")))
                .toList();
        return new VolumeIssueSegment(VolumeIssueType.VOLUME_DROP,
                start, end, samples);
    }
}
