package com.audioagent.analysis.loudness;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LoudnessEvaluator {

    private static final String LOW_SUGGESTION =
            "整体音量偏低，建议适当提升响度。";
    private static final String HIGH_SUGGESTION =
            "整体音量偏高，建议降低增益并检查峰值。";
    private static final String PEAK_RISK_SUGGESTION =
            "检测到较高峰值，调整时注意避免失真。";
    private static final String NARROW_SUGGESTION =
            "音量变化较小，整体动态较为平坦。";
    private static final String WIDE_SUGGESTION =
            "音量变化较大，建议检查段落间音量一致性。";

    private final AnalysisProperties properties;

    public LoudnessEvaluation evaluate(LoudnessMetrics metrics) {
        AnalysisProperties.Loudness config = properties.getLoudness();
        List<String> suggestions = new ArrayList<>();

        String loudnessLevel = classifyLoudness(
                metrics.integratedLoudnessLufs(), config);
        if ("LOW".equals(loudnessLevel)) {
            suggestions.add(LOW_SUGGESTION);
        } else if ("HIGH".equals(loudnessLevel)) {
            suggestions.add(HIGH_SUGGESTION);
        }

        String peakRisk = classifyPeak(metrics.truePeakDbfs(), config);
        if ("RISK".equals(peakRisk)) {
            suggestions.add(PEAK_RISK_SUGGESTION);
        }

        String dynamicRangeLevel = classifyDynamicRange(
                metrics.loudnessRangeLu(), config);
        if ("NARROW".equals(dynamicRangeLevel)) {
            suggestions.add(NARROW_SUGGESTION);
        } else if ("WIDE".equals(dynamicRangeLevel)) {
            suggestions.add(WIDE_SUGGESTION);
        }

        return new LoudnessEvaluation(loudnessLevel, peakRisk,
                dynamicRangeLevel, List.copyOf(suggestions));
    }

    String classifyLoudness(BigDecimal integrated,
                            AnalysisProperties.Loudness config) {
        BigDecimal lowBoundary = config.getTargetLufs()
                .subtract(config.getToleranceLu());
        BigDecimal highBoundary = config.getTargetLufs()
                .add(config.getToleranceLu());
        if (integrated.compareTo(lowBoundary) < 0) {
            return "LOW";
        }
        if (integrated.compareTo(highBoundary) > 0) {
            return "HIGH";
        }
        return "NORMAL";
    }

    String classifyPeak(BigDecimal truePeak,
                        AnalysisProperties.Loudness config) {
        return truePeak != null
                && truePeak.compareTo(config.getTruePeakLimitDbfs()) > 0
                ? "RISK" : "NORMAL";
    }

    String classifyDynamicRange(BigDecimal lra,
                                AnalysisProperties.Loudness config) {
        if (lra == null) {
            return null;
        }
        if (lra.compareTo(config.getLraMinLu()) < 0) {
            return "NARROW";
        }
        if (lra.compareTo(config.getLraMaxLu()) > 0) {
            return "WIDE";
        }
        return "NORMAL";
    }
}
