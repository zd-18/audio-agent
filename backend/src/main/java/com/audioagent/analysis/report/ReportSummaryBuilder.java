package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioIssueSegment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportSummaryBuilder {

    public String build(List<AudioIssueSegment> issues) {
        List<AudioIssueSegment> safeIssues = issues == null
                ? List.of() : issues;
        if (safeIssues.isEmpty()) {
            return "本段音频整体质量良好，未发现明显的静音、音量波动或背景噪声风险。";
        }
        Set<String> types = safeIssues.stream()
                .map(AudioIssueSegment::getIssueType)
                .collect(Collectors.toSet());
        if (types.size() == 1 && types.contains("SILENCE")) {
            return "检测到 " + safeIssues.size()
                    + " 段较长静音，建议检查是否需要剪除或缩短。";
        }
        if (types.size() == 1 && types.contains("NOISE_RISK")) {
            return "检测到疑似背景噪声片段，部分区域可能影响语音清晰度。";
        }
        boolean hasHigh = safeIssues.stream()
                .anyMatch(issue -> "HIGH".equals(issue.getSeverity()));
        String priority = hasHigh ? "，建议优先处理高严重程度片段" :
                "，建议按时间轴逐段试听确认";
        return "本段音频存在" + joinedIssueNames(types) + priority + "。";
    }

    private String joinedIssueNames(Set<String> types) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        if (types.contains("SILENCE")) {
            names.add("静音");
        }
        if (types.contains("VOLUME_DROP") || types.contains("VOLUME_SPIKE")) {
            names.add("音量波动");
        }
        if (types.contains("NOISE_RISK")) {
            names.add("噪声风险");
        }
        return String.join("、", names);
    }
}
