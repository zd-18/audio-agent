package com.audioagent.analysis.volume;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record VolumeIssueSegment(
        VolumeIssueType issueType,
        long startMs,
        long endMs,
        List<VolumeSampleWindow> samples
) {

    public VolumeIssueSegment {
        samples = List.copyOf(samples);
        if (startMs < 0 || endMs < startMs) {
            throw new IllegalArgumentException("Invalid volume segment range");
        }
    }

    public long durationMs() {
        return endMs - startMs;
    }

    public int sampleCount() {
        return samples.size();
    }

    public BigDecimal averageMomentaryLufs() {
        if (samples.isEmpty()) {
            return null;
        }
        BigDecimal sum = samples.stream()
                .map(VolumeSampleWindow::momentaryLufs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(samples.size()),
                4, RoundingMode.HALF_UP);
    }

    public BigDecimal minimumMomentaryLufs() {
        return samples.stream()
                .map(VolumeSampleWindow::momentaryLufs)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    public BigDecimal maximumMomentaryLufs() {
        return samples.stream()
                .map(VolumeSampleWindow::momentaryLufs)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    public BigDecimal deviationLu(BigDecimal baseline) {
        BigDecimal average = averageMomentaryLufs();
        if (average == null || baseline == null) {
            return BigDecimal.ZERO;
        }
        return issueType == VolumeIssueType.VOLUME_DROP
                ? baseline.subtract(average).max(BigDecimal.ZERO)
                : average.subtract(baseline).max(BigDecimal.ZERO);
    }
}
