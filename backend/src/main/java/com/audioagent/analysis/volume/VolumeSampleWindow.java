package com.audioagent.analysis.volume;

import java.math.BigDecimal;

public record VolumeSampleWindow(
        VolumeIssueType issueType,
        long startMs,
        long endMs,
        BigDecimal momentaryLufs
) {

    public long durationMs() {
        return Math.max(0, endMs - startMs);
    }
}
