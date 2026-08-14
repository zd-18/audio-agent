package com.audioagent.file.multipart.dto;

import lombok.Data;

@Data
public class MultipartUploadInitRequest {

    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private Long chunkSize;
    private Integer totalChunks;
}
