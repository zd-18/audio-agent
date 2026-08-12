package com.audioagent.analysis.entity;

import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.enums.AnalysisType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_analysis_task")
public class AudioAnalysisTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long audioFileId;

    private AnalysisType analysisType;

    private AnalysisTaskStatus status;

    private Integer progress;

    private String errorMessage;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryAt;

    private String lastErrorCode;

    private String lastMessageId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
