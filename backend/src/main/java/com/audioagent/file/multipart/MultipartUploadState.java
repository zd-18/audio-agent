package com.audioagent.file.multipart;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultipartUploadState {

    private String uploadId;
    private Long userId;
    private String originalName;
    private String extension;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private String resumeFingerprint;
    private Long chunkSize;
    private Integer totalChunks;
    private String finalObjectKey;
    private MultipartUploadStatus status;
    private Long audioFileId;
    private Long createdAt;
    private Long updatedAt;
}
