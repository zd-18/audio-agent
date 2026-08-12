package com.audioagent.analysis.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskListVO {

    private Long taskId;
    private Long audioFileId;
    private String fileName;
    private String analysisType;
    private String status;
    private Integer progress;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String lastErrorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
