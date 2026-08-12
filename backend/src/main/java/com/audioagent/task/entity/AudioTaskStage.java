package com.audioagent.task.entity;

import com.audioagent.common.enums.StageStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_task_stage")
public class AudioTaskStage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private String stageCode;

    private Integer attemptNo;

    private StageStatus status;

    private Integer progress;

    private Long inputFileId;

    private Long outputFileId;

    private String inputSnapshot;

    private String outputSnapshot;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
