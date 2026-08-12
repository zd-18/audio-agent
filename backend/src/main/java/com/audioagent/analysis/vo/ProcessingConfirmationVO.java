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
public class ProcessingConfirmationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long confirmationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;
    private Integer sourcePlanRevision;
    private String confirmationStatus;
    private Integer acceptedStepCount;
    private Integer rejectedStepCount;
    private Integer pendingStepCount;
    private String resultMessage;
    @Builder.Default
    private List<Step> steps = List.of();
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Step {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long stepConfirmationId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long sourceStepId;
        private Integer stepOrder;
        private String operationType;
        private String title;
        private String decision;
        private Boolean userConfirmed;
        private Boolean requiresConfirmation;
        private Long startMs;
        private Long endMs;
        @Builder.Default
        private Map<String, Object> originalParameters = Map.of();
        @Builder.Default
        private Map<String, Object> parameterOverrides = Map.of();
        @Builder.Default
        private Map<String, Object> effectiveParameters = Map.of();
        private String userNote;
    }
}

