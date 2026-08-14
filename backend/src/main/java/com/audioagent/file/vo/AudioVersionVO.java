package com.audioagent.file.vo;

import com.audioagent.file.entity.AudioFile;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AudioVersionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentAudioFileId;

    private Integer versionNo;
    private String versionSummary;
    private boolean originalVersion;
    private String fileName;
    private String extension;
    private String mimeType;
    private Long sizeBytes;
    private Long durationMs;
    private LocalDateTime createdAt;

    public static AudioVersionVO from(AudioFile file) {
        int number = file.getVersionNo() == null ? 0 : file.getVersionNo();
        return AudioVersionVO.builder()
                .audioFileId(file.getId())
                .parentAudioFileId(file.getSourceFileId())
                .versionNo(number)
                .versionSummary(file.getVersionSummary())
                .originalVersion(number == 0)
                .fileName(file.getOriginalName())
                .extension(file.getExtension())
                .mimeType(file.getMimeType())
                .sizeBytes(file.getSizeBytes())
                .durationMs(file.getDurationMs())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
