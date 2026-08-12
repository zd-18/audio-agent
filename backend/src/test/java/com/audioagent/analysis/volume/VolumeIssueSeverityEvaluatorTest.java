package com.audioagent.analysis.volume;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeIssueSeverityEvaluatorTest {

    private final VolumeIssueSeverityEvaluator evaluator =
            new VolumeIssueSeverityEvaluator(new AnalysisProperties());

    @Test
    void classifiesLowMediumAndHighFromDurationOrDeviation() {
        BigDecimal baseline = new BigDecimal("-20");

        assertEquals("LOW", evaluator.evaluate(
                drop(0, 1_500, "-25"), baseline));
        assertEquals("MEDIUM", evaluator.evaluate(
                drop(0, 3_000, "-25"), baseline));
        assertEquals("MEDIUM", evaluator.evaluate(
                drop(0, 1_500, "-31"), baseline));
        assertEquals("HIGH", evaluator.evaluate(
                drop(0, 1_500, "-36"), baseline));
        assertEquals("HIGH", evaluator.evaluate(
                drop(0, 8_000, "-25"), baseline));
    }

    private VolumeIssueSegment drop(long start, long end, String value) {
        return new VolumeIssueSegment(VolumeIssueType.VOLUME_DROP,
                start, end, List.of(new VolumeSampleWindow(
                VolumeIssueType.VOLUME_DROP, start, end,
                new BigDecimal(value))));
    }
}
