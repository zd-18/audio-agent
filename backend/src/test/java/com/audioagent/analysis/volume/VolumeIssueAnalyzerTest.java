package com.audioagent.analysis.volume;

import com.audioagent.analysis.loudness.LoudnessAnalysis;
import com.audioagent.analysis.loudness.LoudnessFrame;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeIssueAnalyzerTest {

    private AnalysisProperties properties;
    private VolumeIssueAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        properties = new AnalysisProperties();
        properties.getVolumeSegment().setMinDurationMs(200);
        properties.getVolumeSegment().setMergeGapMs(0);
        analyzer = new VolumeIssueAnalyzer(properties,
                new VolumeIssueSegmentMerger(properties));
    }

    @Test
    void detectsRelativeDropsAndSpikesUsingIntegratedBaseline() {
        LoudnessAnalysis analysis = analysis(List.of(
                frame(0, "-30"), frame(100, "-31"),
                frame(200, "-20"), frame(300, "-10"),
                frame(400, "-9"), frame(500, "-20")));

        List<VolumeIssueSegment> result = analyzer.analyze(analysis, 600);

        assertEquals(2, result.size());
        assertEquals(VolumeIssueType.VOLUME_DROP,
                result.get(0).issueType());
        assertEquals(0, result.get(0).startMs());
        assertEquals(200, result.get(0).endMs());
        assertEquals(VolumeIssueType.VOLUME_SPIKE,
                result.get(1).issueType());
        assertEquals(300, result.get(1).startMs());
        assertEquals(500, result.get(1).endMs());
    }

    @Test
    void filtersInfinityAndNearSilenceValues() {
        LoudnessAnalysis analysis = analysis(List.of(
                new LoudnessFrame(0, null, null),
                frame(100, "-80"), frame(200, "-20")));

        assertTrue(analyzer.analyze(analysis, 300).isEmpty());
    }

    @Test
    void disabledConfigurationSkipsSegmentAnalysis() {
        properties.getVolumeSegment().setEnabled(false);

        assertTrue(analyzer.analyze(analysis(List.of(
                frame(0, "-40"), frame(100, "-40"),
                frame(200, "-20"))), 300).isEmpty());
    }

    private LoudnessAnalysis analysis(List<LoudnessFrame> frames) {
        return new LoudnessAnalysis(new LoudnessMetrics(
                new BigDecimal("-20"), BigDecimal.ZERO,
                null, null), frames);
    }

    private LoudnessFrame frame(long timestamp, String momentary) {
        return new LoudnessFrame(timestamp,
                new BigDecimal(momentary), null);
    }
}
