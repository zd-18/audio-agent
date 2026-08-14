package com.audioagent.file.multipart.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MultipartUploadProgressVO {

    private String uploadId;
    private String status;
    private Long sizeBytes;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedCount;
    private List<Integer> uploadedChunks;
}
