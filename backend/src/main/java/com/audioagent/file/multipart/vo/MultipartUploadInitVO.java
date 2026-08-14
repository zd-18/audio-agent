package com.audioagent.file.multipart.vo;

import com.audioagent.file.vo.AudioFileVO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MultipartUploadInitVO {

    private String uploadId;
    private String status;
    private boolean instantUpload;
    private Long chunkSize;
    private Integer totalChunks;
    private List<Integer> uploadedChunks;
    private AudioFileVO audioFile;
}
