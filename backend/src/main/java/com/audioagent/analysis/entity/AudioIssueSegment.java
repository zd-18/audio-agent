package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_issue_segment")
public class AudioIssueSegment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long audioFileId;

    private String issueType;

    private Long startMs;

    private Long endMs;

    private Long durationMs;

    private String severity;

    private String metricJson;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
