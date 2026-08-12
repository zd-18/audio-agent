package com.audioagent.processing.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProcessingExecutionListVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    private String fileName;
    private String executionStatus;
    private String currentStage;
    private Integer progressPercent;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resultFileId;
    private String failureCode;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
