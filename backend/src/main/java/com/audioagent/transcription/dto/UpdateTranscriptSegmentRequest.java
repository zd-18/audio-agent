package com.audioagent.transcription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTranscriptSegmentRequest {

    @NotBlank(message = "片段文字不能为空")
    @Size(max = 5000, message = "片段文字不能超过 5000 个字符")
    private String text;

    @Size(max = 64, message = "说话人名称不能超过 64 个字符")
    private String speaker;
}
