package com.audioagent.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("audio_analysis_result")
public class AudioAnalysisResult {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long audioFileId;

    private String formatName;

    private String codecName;

    private Long durationMs;

    private Integer sampleRate;

    private Integer channels;

    private Long bitRate;

    private Long fileSize;

    private Integer issueCount;

    private Integer silenceCount;

    private Long totalSilenceDurationMs;

    private BigDecimal silenceRatio;

    private BigDecimal integratedLoudnessLufs;

    private BigDecimal loudnessRangeLu;

    private BigDecimal samplePeakDbfs;

    private BigDecimal truePeakDbfs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
