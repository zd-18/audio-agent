package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_analysis_report")
public class AudioAnalysisReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long audioFileId;
    private String reportVersion;
    private Integer qualityScore;
    private String qualityGrade;
    private String summary;
    private String reportJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
