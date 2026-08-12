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
            "SILENCE", "建议试听该片段，确认是否需要删除或缩短静音。",
            "VOLUME_DROP", "该片段音量偏低，建议适当提升增益并与前后内容保持一致。",
            "VOLUME_SPIKE", "该片段音量突然升高，建议降低增益并检查是否存在失真。",
            "NOISE_RISK", "该片段疑似存在背景噪声，建议试听确认后进行轻度降噪。"
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
            addGeneral(recommendations, "MEDIUM",
                    "整体音量偏低，建议适当提升整体响度。");
        } else if ("HIGH".equals(evaluation.loudnessLevel())) {
            addGeneral(recommendations, "HIGH",
                    "整体音量偏高，建议降低整体增益并检查峰值。");
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
