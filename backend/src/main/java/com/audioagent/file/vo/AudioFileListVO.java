package com.audioagent.file.vo;

import com.audioagent.file.entity.AudioFile;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AudioFileListVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;
    private String originalFileName;
    private Long fileSize;
    private String contentType;
    private Long duration;
    private String status;
    private String sha256;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long transcriptionTaskId;
    private String transcriptionStatus;

    public static AudioFileListVO from(AudioFile audioFile) {
        return AudioFileListVO.builder()
                .audioFileId(audioFile.getId())
                .originalFileName(audioFile.getOriginalName())
                .fileSize(audioFile.getSizeBytes())
                .contentType(audioFile.getMimeType())
                .duration(audioFile.getDurationMs())
                .status(audioFile.getFileStatus() == null
                        ? null : audioFile.getFileStatus().name())
                .sha256(audioFile.getSha256())
                .createdAt(audioFile.getCreatedAt())
                .deletedAt(audioFile.getDeletedAt())
                .build();
    }

    public void applyTranscription(AudioTranscriptionTask task) {
        if (task == null) {
            return;
        }
        transcriptionTaskId = task.getId();
        transcriptionStatus = task.getStatus() == null
                ? null : task.getStatus().name();
    }
}
