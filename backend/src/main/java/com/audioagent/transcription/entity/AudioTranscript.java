package com.audioagent.transcription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_transcript")
public class AudioTranscript {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long audioFileId;
    private Long transcriptionTaskId;
    private String language;
    private String fullText;
    private Long durationMs;
    private Integer speakerCount;
    private Integer segmentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
