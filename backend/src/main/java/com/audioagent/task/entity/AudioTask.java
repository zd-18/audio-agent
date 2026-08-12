package com.audioagent.task.entity;

import com.audioagent.common.enums.AudioTaskStatus;
import com.audioagent.common.enums.TaskType;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_task")
public class AudioTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String taskNo;

    private Long userId;

    private Long sourceFileId;

    private Long processedFileId;

    private TaskType taskType;

    private AudioTaskStatus status;

    private String currentStage;

    private Integer progress;

    private String goalText;

    private String processConfig;

    private String resultSummary;

    private String failureStage;

    private String failureCode;

    private String failureMessage;

    private Integer retryCount;

    @Version
    private Integer version;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
