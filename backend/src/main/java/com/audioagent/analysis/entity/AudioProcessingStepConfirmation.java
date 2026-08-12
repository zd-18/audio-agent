package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_step_confirmation")
public class AudioProcessingStepConfirmation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long confirmationId;
    private Long sourceStepId;
    private String decision;
    private Boolean userConfirmed;
    private String parameterOverridesJson;
    private String effectiveParametersJson;
    private String userNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

