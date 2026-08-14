package com.audioagent.file.entity;

import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audio_file")
public class AudioFile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long sourceFileId;

    private Long rootAudioFileId;

    private Integer versionNo;

    private String versionSummary;

    private Long sourceExecutionId;

    private FileRole fileRole;

    private String originalName;

    private String extension;

    private String mimeType;

    private String bucketName;

    private String objectKey;

    private Long sizeBytes;

    private String sha256;

    private Long durationMs;

    private Integer sampleRate;

    private Integer channels;

    private Integer bitRate;

    private FileStatus fileStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
