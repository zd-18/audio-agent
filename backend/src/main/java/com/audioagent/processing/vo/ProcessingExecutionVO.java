package com.audioagent.processing.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingExecutionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long confirmationId;
    private String executionStatus;
    private String currentStage;
    private Integer progressPercent;
    private Integer acceptedStepCount;
    private Integer executableStepCount;
    private Integer skippedStepCount;
    private Integer retryCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resultFileId;
    private String failureCode;
    private String failureMessage;
    @Builder.Default
    private List<Step> steps = List.of();
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Step {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long executionStepId;
        private Integer stepOrder;
        private String operationType;
        private String executionStatus;
        private Long startMs;
        private Long endMs;
        private String skipReason;
        private String failureMessage;
    }
}
