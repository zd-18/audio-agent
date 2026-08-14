package com.audioagent.file.multipart.vo;

import com.audioagent.file.vo.AudioFileVO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MultipartUploadCompleteVO {

    private String uploadId;
    private String status;
    private boolean instantUpload;
    private AudioFileVO audioFile;
}
