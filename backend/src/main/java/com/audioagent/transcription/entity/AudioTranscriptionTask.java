package com.audioagent.transcription.entity;

import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_transcription_task")
public class AudioTranscriptionTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long audioFileId;
    private TranscriptionTaskStatus status;
    private String language;
    private Boolean enableSpeakerDiarization;
    private Integer progressPercent;
    private String provider;
    private String modelName;
    private Integer retryCount;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
