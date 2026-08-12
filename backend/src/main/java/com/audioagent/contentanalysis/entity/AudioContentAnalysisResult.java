package com.audioagent.contentanalysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_content_analysis_result")
public class AudioContentAnalysisResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long taskId;
    private Long transcriptId;

    @TableField("summary_json")
    private String summaryJson;

    @TableField("key_points_json")
    private String keyPointsJson;

    @TableField("chapters_json")
    private String chaptersJson;

    @TableField("speech_issues_json")
    private String speechIssuesJson;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String modelName;
    private String promptVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
