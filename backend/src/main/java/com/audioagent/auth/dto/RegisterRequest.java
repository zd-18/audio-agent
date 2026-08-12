package com.audioagent.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须为 8～64 个字符")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称长度不能超过 100 个字符")
    private String displayName;
}
