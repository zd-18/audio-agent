package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluation;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 可配置的产品启发式评分器。它用于帮助用户确定处理优先级，
 * 不代表行业标准或对音频质量的绝对判定。
 */
@Component
@RequiredArgsConstructor
public class AudioQualityScoreCalculator {

    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 0;

    private final AnalysisProperties properties;
    private final LoudnessEvaluator loudnessEvaluator;

    public ScoreResult calculate(AudioAnalysisResult result,
                                 List<AudioIssueSegment> issues) {
        AnalysisProperties.Report reportConfig = properties.getReport();
        AnalysisProperties.Score scoreConfig = reportConfig.getScore();
        List<AudioIssueSegment> ordered = new ArrayList<>(
                issues == null ? List.of() : issues);
        ordered.sort(ReportIssueCatalog.keyIssueComparator());

        BigDecimal deductions = BigDecimal.ZERO;
        List<AudioIssueSegment> accounted = new ArrayList<>();
        for (AudioIssueSegment issue : ordered) {
            int basePenalty = issuePenalty(issue, scoreConfig);
            if (basePenalty <= 0) {
                continue;
            }
            if (hasHighlyOverlappingSameType(issue, accounted,
                    reportConfig.getOverlapThresholdRatio())) {
                // 同类高度重叠通常是同一现象的重复覆盖，不再次扣分。
                continue;
            }
            BigDecimal penalty = BigDecimal.valueOf(basePenalty);
            if (isHighlyOverlapping(issue, accounted,
                    reportConfig.getOverlapThresholdRatio())) {
                penalty = penalty.multiply(
                        reportConfig.getOverlapDiscountRatio());
            }
            deductions = deductions.add(penalty);
            accounted.add(issue);
        }

        if (result != null && result.getSilenceRatio() != null
                && result.getSilenceRatio().compareTo(
                scoreConfig.getSilenceRatioThreshold()) > 0) {
            deductions = deductions.add(BigDecimal.valueOf(
                    scoreConfig.getSilenceRatioPenalty()));
        }

        deductions = deductions.add(loudnessPenalty(result, scoreConfig));
        int roundedDeductions = deductions.setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int score = Math.max(MIN_SCORE,
                Math.min(MAX_SCORE, MAX_SCORE - roundedDeductions));
        return new ScoreResult(score, QualityGrade.fromScore(score,
                reportConfig.getGrade()));
    }

    private int issuePenalty(AudioIssueSegment issue,
                             AnalysisProperties.Score config) {
        String severity = issue.getSeverity() == null
                ? "LOW" : issue.getSeverity();
        return switch (issue.getIssueType() == null
                ? "" : issue.getIssueType()) {
            case "SILENCE" -> bySeverity(severity, config.getSilenceLow(),
                    config.getSilenceMedium(), config.getSilenceHigh());
            case "VOLUME_DROP" -> bySeverity(severity,
                    config.getVolumeDropLow(), config.getVolumeDropMedium(),
                    config.getVolumeDropHigh());
            case "VOLUME_SPIKE" -> bySeverity(severity,
                    config.getVolumeSpikeLow(),
                    config.getVolumeSpikeMedium(),
                    config.getVolumeSpikeHigh());
            case "NOISE_RISK" -> bySeverity(severity,
                    config.getNoiseRiskLow(), config.getNoiseRiskMedium(),
                    config.getNoiseRiskHigh());
            default -> 0;
        };
    }

    private int bySeverity(String severity, int low, int medium, int high) {
        return switch (severity) {
            case "HIGH" -> high;
            case "MEDIUM" -> medium;
            default -> low;
        };
    }

    private BigDecimal loudnessPenalty(
            AudioAnalysisResult result, AnalysisProperties.Score config) {
        if (result == null || result.getIntegratedLoudnessLufs() == null) {
            return BigDecimal.ZERO;
        }
        LoudnessMetrics metrics = new LoudnessMetrics(
                result.getIntegratedLoudnessLufs(),
                result.getLoudnessRangeLu(), result.getSamplePeakDbfs(),
                result.getTruePeakDbfs());
        LoudnessEvaluation evaluation = loudnessEvaluator.evaluate(metrics);
        int penalty = 0;
        if (!"NORMAL".equals(evaluation.loudnessLevel())) {
            penalty += config.getLoudnessAbnormal();
        }
        if ("RISK".equals(evaluation.peakRisk())) {
            penalty += config.getPeakRisk();
        }
        if ("NARROW".equals(evaluation.dynamicRangeLevel())) {
            penalty += config.getDynamicRangeNarrow();
        } else if ("WIDE".equals(evaluation.dynamicRangeLevel())) {
            penalty += config.getDynamicRangeWide();
        }
        return BigDecimal.valueOf(penalty);
    }

    private boolean isHighlyOverlapping(AudioIssueSegment candidate,
                                        List<AudioIssueSegment> accounted,
                                        BigDecimal threshold) {
        return accounted.stream().anyMatch(existing ->
                overlapRatio(candidate, existing).compareTo(threshold) >= 0);
    }

    private boolean hasHighlyOverlappingSameType(
            AudioIssueSegment candidate,
            List<AudioIssueSegment> accounted,
            BigDecimal threshold) {
        return accounted.stream().anyMatch(existing ->
                java.util.Objects.equals(candidate.getIssueType(),
                        existing.getIssueType())
                        && overlapRatio(candidate, existing)
                        .compareTo(threshold) >= 0);
    }

    private BigDecimal overlapRatio(AudioIssueSegment first,
                                    AudioIssueSegment second) {
        if (first.getStartMs() == null || first.getEndMs() == null
                || second.getStartMs() == null || second.getEndMs() == null) {
            return BigDecimal.ZERO;
        }
        long overlap = Math.max(0L, Math.min(first.getEndMs(),
                second.getEndMs()) - Math.max(first.getStartMs(),
                second.getStartMs()));
        long firstDuration = positiveDuration(first);
        long secondDuration = positiveDuration(second);
        long denominator = Math.min(firstDuration, secondDuration);
        if (overlap == 0 || denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(overlap).divide(
                BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private long positiveDuration(AudioIssueSegment issue) {
        if (issue.getDurationMs() != null && issue.getDurationMs() > 0) {
            return issue.getDurationMs();
        }
        return Math.max(0L, issue.getEndMs() - issue.getStartMs());
    }

    public record ScoreResult(int score, QualityGrade grade) {
    }
}
