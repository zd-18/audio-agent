package com.audioagent.processing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_execution")
public class AudioProcessingExecution {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long taskId;
    private Long audioFileId;
    private Long confirmationId;
    private Long sourcePlanId;
    private Integer sourcePlanRevision;
    private String executionStatus;
    private Integer acceptedStepCount;
    private Integer executableStepCount;
    private Integer skippedStepCount;
    private String currentStage;
    private Integer progressPercent;
    private Long resultFileId;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
