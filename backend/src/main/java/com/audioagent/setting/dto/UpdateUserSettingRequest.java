package com.audioagent.setting.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateUserSettingRequest {

    @NotBlank(message = "默认降噪强度不能为空")
    private String defaultDenoiseStrength;

    @NotBlank(message = "处理策略不能为空")
    private String processingStrategy;

    @NotNull(message = "请选择是否自动限制峰值")
    private Boolean autoLimitPeak;

    @NotNull(message = "请选择处理步骤确认偏好")
    private Boolean requireStepConfirmation;

    @NotNull(message = "请选择是否保持播放位置")
    private Boolean preservePlaybackPosition;

    @NotNull(message = "问题片段上下文秒数不能为空")
    @Min(value = 0, message = "问题片段上下文秒数不能小于 0")
    @Max(value = 10, message = "问题片段上下文秒数不能大于 10")
    private Integer issueContextSeconds;

    @NotNull(message = "默认播放音量不能为空")
    @DecimalMin(value = "0.0", message = "默认播放音量不能小于 0")
    @DecimalMax(value = "1.0", message = "默认播放音量不能大于 1")
    private BigDecimal defaultPlaybackVolume;

    @NotNull(message = "默认分页数量不能为空")
    private Integer defaultPageSize;

    @NotNull(message = "请选择是否显示任务完成提示")
    private Boolean notifyOnTaskComplete;

    @NotNull(message = "请选择是否自动定位处理结果")
    private Boolean autoOpenResultPage;
}
