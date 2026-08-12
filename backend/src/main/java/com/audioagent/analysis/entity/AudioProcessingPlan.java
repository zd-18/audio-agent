package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_processing_plan")
public class AudioProcessingPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long audioFileId;
    private Integer planVersion;
    private Integer planRevision;
    private String planStatus;
    private String summary;
    private Integer stepCount;
    private Long estimatedOutputDurationMs;
    private String planJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
