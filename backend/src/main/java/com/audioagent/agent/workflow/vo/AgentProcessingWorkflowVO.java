package com.audioagent.agent.workflow.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentProcessingWorkflowVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userMessageId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assistantMessageId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long confirmationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resultFileId;
    private String status;
    private String summary;
    @Builder.Default
    private List<Step> steps = List.of();
    private Integer progressPercent;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;

    @Getter
    @Builder
    public static class Step {
        private Integer order;
        private String operationType;
        private String title;
        private String reason;
        private Long startMs;
        private Long endMs;
    }
}
