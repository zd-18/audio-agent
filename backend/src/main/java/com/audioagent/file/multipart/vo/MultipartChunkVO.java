package com.audioagent.file.multipart.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MultipartChunkVO {

    private String uploadId;
    private String status;
    private Integer chunkIndex;
    private Integer uploadedCount;
    private Integer totalChunks;
}
