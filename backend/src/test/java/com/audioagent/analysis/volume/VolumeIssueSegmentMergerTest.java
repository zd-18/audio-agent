package com.audioagent.analysis.volume;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeIssueSegmentMergerTest {

    private VolumeIssueSegmentMerger merger;

    @BeforeEach
    void setUp() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.getVolumeSegment().setMinDurationMs(200);
        properties.getVolumeSegment().setMergeGapMs(150);
        merger = new VolumeIssueSegmentMerger(properties);
    }

    @Test
    void mergesContinuousAndSmallGapWindowsOfSameType() {
        List<VolumeIssueSegment> result = merger.merge(List.of(
                window(VolumeIssueType.VOLUME_DROP, 0, 100, "-30"),
                window(VolumeIssueType.VOLUME_DROP, 100, 200, "-31"),
                window(VolumeIssueType.VOLUME_DROP, 300, 400, "-29")),
                1_000);

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().startMs());
        assertEquals(400, result.getFirst().endMs());
        assertEquals(3, result.getFirst().sampleCount());
    }

    @Test
    void neverMergesDifferentIssueTypes() {
        List<VolumeIssueSegment> result = merger.merge(List.of(
                window(VolumeIssueType.VOLUME_DROP, 0, 100, "-30"),
                window(VolumeIssueType.VOLUME_DROP, 100, 200, "-31"),
                window(VolumeIssueType.VOLUME_SPIKE, 200, 300, "-8"),
                window(VolumeIssueType.VOLUME_SPIKE, 300, 400, "-7")),
                1_000);

        assertEquals(2, result.size());
        assertEquals(VolumeIssueType.VOLUME_DROP,
                result.get(0).issueType());
        assertEquals(VolumeIssueType.VOLUME_SPIKE,
                result.get(1).issueType());
    }

    @Test
    void filtersSegmentsShorterThanConfiguredMinimum() {
        assertEquals(0, merger.merge(List.of(
                window(VolumeIssueType.VOLUME_DROP, 0, 100, "-30")),
                1_000).size());
    }

    private VolumeSampleWindow window(VolumeIssueType type,
                                      long start, long end, String value) {
        return new VolumeSampleWindow(type, start, end,
                new BigDecimal(value));
    }
}
