package com.audioagent.file.vo;

import com.audioagent.file.entity.AudioFile;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AudioFileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String originalName;

    private String extension;

    private String mimeType;

    private Long sizeBytes;

    private String sha256;

    private String fileRole;

    private String fileStatus;

    private Integer versionNo;

    private String versionSummary;

    private Long durationMs;

    private LocalDateTime createdAt;

    public static AudioFileVO from(AudioFile audioFile) {
        return AudioFileVO.builder()
                .fileId(audioFile.getId())
                .originalName(audioFile.getOriginalName())
                .extension(audioFile.getExtension())
                .mimeType(audioFile.getMimeType())
                .sizeBytes(audioFile.getSizeBytes())
                .sha256(audioFile.getSha256())
                .fileRole(
                        audioFile.getFileRole() == null
                                ? null
                                : audioFile.getFileRole().name()
                )
                .fileStatus(
                        audioFile.getFileStatus() == null
                                ? null
                                : audioFile.getFileStatus().name()
                )
                .versionNo(audioFile.getVersionNo())
                .versionSummary(audioFile.getVersionSummary())
                .durationMs(audioFile.getDurationMs())
                .createdAt(audioFile.getCreatedAt())
                .build();
    }
}
