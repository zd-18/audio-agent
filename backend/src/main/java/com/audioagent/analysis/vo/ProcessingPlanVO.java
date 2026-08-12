package com.audioagent.analysis.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingPlanVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    private Integer planVersion;
    private Integer planRevision;
    private String planStatus;
    private String summary;
    private Integer stepCount;
    private Long estimatedOutputDurationMs;
    @Builder.Default
    private List<Step> steps = List.of();
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Step {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long stepId;
        private Integer stepOrder;
        private String operationType;
        private String title;
        private String description;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceIssueId;
        private Long startMs;
        private Long endMs;
        private String priority;
        private String riskLevel;
        private Boolean requiresConfirmation;
        @Builder.Default
        private Map<String, Object> parameters = Map.of();
        private String reason;
    }
}
