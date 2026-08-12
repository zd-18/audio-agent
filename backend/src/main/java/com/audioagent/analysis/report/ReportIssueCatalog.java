package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioIssueSegment;

import java.util.Comparator;
import java.util.Map;

final class ReportIssueCatalog {

    private static final Map<String, Integer> TYPE_PRIORITY = Map.of(
            "NOISE_RISK", 0,
            "VOLUME_SPIKE", 1,
            "VOLUME_DROP", 2,
            "SILENCE", 3
    );

    private ReportIssueCatalog() {
    }

    static int severityRank(String severity) {
        return switch (safe(severity)) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    static int typeRank(String issueType) {
        return TYPE_PRIORITY.getOrDefault(issueType, 99);
    }

    static Comparator<AudioIssueSegment> keyIssueComparator() {
        return Comparator
                .comparingInt((AudioIssueSegment issue) ->
                        severityRank(issue.getSeverity()))
                .thenComparingInt(issue -> typeRank(issue.getIssueType()))
                .thenComparing(AudioIssueSegment::getDurationMs,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AudioIssueSegment::getStartMs,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static String title(String issueType) {
        return switch (safe(issueType)) {
            case "SILENCE" -> "较长静音";
            case "VOLUME_DROP" -> "音量偏低";
            case "VOLUME_SPIKE" -> "音量突然升高";
            case "NOISE_RISK" -> "背景噪声风险";
            default -> "音频质量问题";
        };
    }

    static String description(String issueType) {
        return switch (safe(issueType)) {
            case "SILENCE" -> "该时间段持续无明显声音，建议试听确认是否需要保留。";
            case "VOLUME_DROP" -> "该时间段音量明显低于整段音频的平均水平。";
            case "VOLUME_SPIKE" -> "该时间段音量明显高于整段音频的平均水平。";
            case "NOISE_RISK" -> "该时间段疑似存在持续背景噪声，建议试听确认。";
            default -> "该时间段存在需要关注的音频质量现象。";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
