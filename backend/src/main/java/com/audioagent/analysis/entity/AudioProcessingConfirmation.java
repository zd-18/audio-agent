package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_confirmation")
public class AudioProcessingConfirmation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long audioFileId;
    private Long planId;
    private Integer sourcePlanRevision;
    private String confirmationStatus;
    private Integer acceptedStepCount;
    private Integer rejectedStepCount;
    private Integer pendingStepCount;
    private String confirmationJson;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

