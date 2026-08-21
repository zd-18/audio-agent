package com.audioagent.analysis.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioAnalysisReportVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;
    private String reportVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    private Integer qualityScore;
    private String qualityGrade;
    private String qualityGradeText;
    private String summary;
    private AudioOverview audioOverview;
    private LoudnessOverview loudnessOverview;
    private IssueSummary issueSummary;
    private List<ReportIssue> keyIssues;
    private List<ReportIssue> timeline;
    private List<Recommendation> recommendations;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioOverview {
        private String fileName;
        private Long durationMs;
        private String format;
        private String codec;
        private Integer sampleRate;
        private Integer channels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoudnessOverview {
        private BigDecimal integratedLoudnessLufs;
        private BigDecimal loudnessRangeLu;
        private BigDecimal truePeakDbfs;
        private String loudnessLevel;
        private String peakRisk;
        private String dynamicRangeLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueSummary {
        private Integer totalIssueCount;
        private Long totalIssueDurationMs;
        private Integer silenceCount;
        private Integer volumeDropCount;
        private Integer volumeSpikeCount;
        private Integer noiseRiskCount;
        private Long silenceDurationMs;
        private Long volumeIssueDurationMs;
        private Long noiseRiskDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportIssue {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long issueId;
        private String issueType;
        private String title;
        private Long startMs;
        private Long endMs;
        private Long durationMs;
        private String severity;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Recommendation {
        private String priority;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long issueId;
        private Long startMs;
        private Long endMs;
        private String message;
        private String recommendedMethod;
        private String recommendedParameters;
    }

    /** 持久化 JSON，仅包含最终用户内容；关联身份和摘要使用表列存储。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private AudioOverview audioOverview;
        private LoudnessOverview loudnessOverview;
        private IssueSummary issueSummary;
        private List<ReportIssue> keyIssues;
        private List<ReportIssue> timeline;
        private List<Recommendation> recommendations;
    }
}
