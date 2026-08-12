package com.audioagent.contentanalysis.entity;

import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_content_analysis_task")
public class AudioContentAnalysisTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long transcriptId;
    private ContentAnalysisTaskStatus status;

    @TableField("analysis_types")
    private String analysisTypesJson;

    private String summaryStyle;
    private Integer progressPercent;
    private String modelName;
    private String promptVersion;
    private Integer retryCount;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
