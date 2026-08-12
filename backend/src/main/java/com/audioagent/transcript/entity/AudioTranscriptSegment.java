package com.audioagent.transcript.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("audio_transcript_segment")
public class AudioTranscriptSegment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long transcriptId;
    private Integer segmentOrder;

    private Long startMs;

    private Long endMs;

    @TableField("speaker_label")
    private String speakerLabel;

    private String text;

    private BigDecimal confidence;

    private LocalDateTime createdAt;
}
