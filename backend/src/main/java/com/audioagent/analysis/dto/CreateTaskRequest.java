package com.audioagent.analysis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateTaskRequest {

    @NotNull(message = "音频文件ID不能为空")
    @Positive(message = "音频文件ID必须大于0")
    private Long audioFileId;

    private String analysisType;
}
