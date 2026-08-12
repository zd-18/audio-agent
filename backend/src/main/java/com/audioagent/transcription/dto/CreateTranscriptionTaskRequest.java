package com.audioagent.transcription.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateTranscriptionTaskRequest {

    @NotNull(message = "音频文件ID不能为空")
    @Positive(message = "音频文件ID必须为正整数")
    private Long audioFileId;

    @Pattern(
            regexp = "[A-Za-z]{2,8}([_-][A-Za-z0-9]{2,8})?",
            message = "语言代码格式不正确"
    )
    private String language;

    private Boolean enableSpeakerDiarization;
}
