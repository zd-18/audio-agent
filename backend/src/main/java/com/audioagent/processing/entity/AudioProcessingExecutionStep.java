package com.audioagent.processing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_execution_step")
public class AudioProcessingExecutionStep {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long executionId;
    private Long sourceStepConfirmationId;
    private Long sourceProcessingStepId;
    private Integer stepOrder;
    private String operationType;
    private String executionStatus;
    private Long startMs;
    private Long endMs;
    private String effectiveParametersJson;
    private String skipReason;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
