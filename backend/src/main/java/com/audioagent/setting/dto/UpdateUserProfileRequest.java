package com.audioagent.setting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserProfileRequest {

    @NotNull(message = "显示名称不能为空")
    @Size(max = 50, message = "显示名称不能超过 50 个字符")
    private String displayName;
}
