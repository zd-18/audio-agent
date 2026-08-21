package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluation;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.Recommendation;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AudioRecommendationBuilder {

    private static final Map<String, String> ISSUE_MESSAGES = Map.of(
            "SILENCE", "检测到连续静音，建议试听后缩短或裁剪。",
            "VOLUME_DROP", "该片段音量偏低，建议与前后内容对比试听。",
            "VOLUME_SPIKE", "该片段音量突然升高，建议检查峰值与失真。",
            "NOISE_RISK", "该片段疑似存在背景噪声，建议试听确认。"
    );

    private final AnalysisProperties properties;
    private final LoudnessEvaluator loudnessEvaluator;

    public List<Recommendation> build(AudioAnalysisResult result,
                                      List<AudioIssueSegment> issues) {
        List<AudioIssueSegment> ordered = new ArrayList<>(
                issues == null ? List.of() : issues);
        ordered.sort(ReportIssueCatalog.keyIssueComparator());

        // 同一类片段只保留最高优先级的一条动作建议，避免文案重复刷屏。
        Map<String, Recommendation> deduplicated = new LinkedHashMap<>();
        for (AudioIssueSegment issue : ordered) {
            String message = ISSUE_MESSAGES.get(issue.getIssueType());
            if (message == null || deduplicated.containsKey(message)) {
                continue;
            }
            deduplicated.put(message, Recommendation.builder()
                    .priority(priorityOf(issue.getSeverity()))
                    .issueId(issue.getId())
                    .startMs(issue.getStartMs())
                    .endMs(issue.getEndMs())
                    .message(message)
                    .recommendedMethod(methodOf(issue))
                    .recommendedParameters(parametersOf(issue))
                    .build());
        }

        appendLoudnessRecommendations(result, deduplicated);
        return deduplicated.values().stream()
                .sorted(Comparator.comparingInt(recommendation ->
                        priorityRank(recommendation.getPriority())))
                .limit(properties.getReport().getMaxRecommendations())
                .toList();
    }

    private void appendLoudnessRecommendations(
            AudioAnalysisResult result,
            Map<String, Recommendation> recommendations) {
        if (result == null || result.getIntegratedLoudnessLufs() == null) {
            return;
        }
        LoudnessEvaluation evaluation = loudnessEvaluator.evaluate(
                new LoudnessMetrics(result.getIntegratedLoudnessLufs(),
                        result.getLoudnessRangeLu(),
                        result.getSamplePeakDbfs(),
                        result.getTruePeakDbfs()));
        if ("LOW".equals(evaluation.loudnessLevel())) {
            addLoudness(recommendations, "MEDIUM", result,
                    "整体响度偏低。");
        } else if ("HIGH".equals(evaluation.loudnessLevel())) {
            addLoudness(recommendations, "HIGH", result,
                    "整体响度偏高。");
        }
        if ("RISK".equals(evaluation.peakRisk())) {
            addGeneral(recommendations, "HIGH",
                    "检测到较高峰值，处理时应注意避免削波和失真。");
        }
        if ("NARROW".equals(evaluation.dynamicRangeLevel())) {
            addGeneral(recommendations, "LOW",
                    "整体动态范围较窄，可试听确认声音是否过于平坦。");
        } else if ("WIDE".equals(evaluation.dynamicRangeLevel())) {
            addGeneral(recommendations, "MEDIUM",
                    "整体动态范围较宽，建议检查不同段落之间的音量一致性。");
        }
    }

    private void addGeneral(Map<String, Recommendation> recommendations,
                            String priority, String message) {
        recommendations.putIfAbsent(message, Recommendation.builder()
                .priority(priority).message(message).build());
    }

    private void addLoudness(Map<String, Recommendation> recommendations,
                             String priority, AudioAnalysisResult result,
                             String message) {
        String current = result.getIntegratedLoudnessLufs() == null
                ? message : message + " 当前："
                + result.getIntegratedLoudnessLufs().stripTrailingZeros()
                .toPlainString() + " LUFS。";
        recommendations.putIfAbsent(current, Recommendation.builder()
                .priority(priority)
                .message(current)
                .recommendedMethod("整段响度标准化")
                .recommendedParameters("目标 "
                        + properties.getLoudness().getTargetLufs()
                        + " LUFS，真峰值上限 "
                        + properties.getLoudness().getTruePeakLimitDbfs()
                        + " dBFS")
                .build());
    }

    private String methodOf(AudioIssueSegment issue) {
        return switch (issue.getIssueType()) {
            case "SILENCE" -> issue.getDurationMs() != null
                    && issue.getDurationMs() >= properties
                    .getProcessingPlan().getSilence().getLongSilenceMinMs()
                    ? "压缩长静音" : "裁剪静音片段";
            case "NOISE_RISK" -> "轻度降噪";
            case "VOLUME_DROP", "VOLUME_SPIKE" -> "平衡局部音量";
            default -> null;
        };
    }

    private String parametersOf(AudioIssueSegment issue) {
        return switch (issue.getIssueType()) {
            case "SILENCE" -> issue.getDurationMs() != null
                    && issue.getDurationMs() >= properties
                    .getProcessingPlan().getSilence().getLongSilenceMinMs()
                    ? "静音超过 " + properties.getProcessingPlan()
                    .getSilence().getLongSilenceMinMs() + " ms 时保留 "
                    + properties.getProcessingPlan().getSilence()
                    .getKeepSilenceMs() + " ms"
                    : "试听确认裁剪范围";
            case "NOISE_RISK" -> "建议强度 "
                    + ("HIGH".equalsIgnoreCase(issue.getSeverity())
                    ? "STRONG" : "MEDIUM");
            case "VOLUME_DROP", "VOLUME_SPIKE" ->
                    "根据上下文试听后确认增益";
            default -> null;
        };
    }

    private String priorityOf(String severity) {
        return switch (severity == null ? "" : severity) {
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}
