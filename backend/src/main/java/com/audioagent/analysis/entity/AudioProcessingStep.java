package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_step")
public class AudioProcessingStep {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long planId;
    private Integer stepOrder;
    private String operationType;
    private String title;
    private String description;
    private Long sourceIssueId;
    private Long startMs;
    private Long endMs;
    private String priority;
    private String riskLevel;
    private Boolean requiresConfirmation;
    private String parametersJson;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
