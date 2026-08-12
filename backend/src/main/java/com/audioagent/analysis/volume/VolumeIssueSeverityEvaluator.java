package com.audioagent.analysis.volume;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class VolumeIssueSeverityEvaluator {

    private final AnalysisProperties properties;

    public String evaluate(VolumeIssueSegment segment,
                           BigDecimal baselineIntegratedLufs) {
        AnalysisProperties.VolumeSegment config =
                properties.getVolumeSegment();
        BigDecimal deviation = segment.deviationLu(
                baselineIntegratedLufs);
        if (segment.durationMs() >= config.getHighDurationMs()
                || deviation.compareTo(
                config.getHighDeviationLu()) >= 0) {
            return "HIGH";
        }
        if (segment.durationMs() >= config.getMediumDurationMs()
                || deviation.compareTo(
                config.getMediumDeviationLu()) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
