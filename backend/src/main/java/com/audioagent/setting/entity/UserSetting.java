package com.audioagent.setting.entity;

import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("user_setting")
public class UserSetting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private DenoiseStrength defaultDenoiseStrength;
    private ProcessingStrategy processingStrategy;
    private Boolean autoLimitPeak;
    private Boolean requireStepConfirmation;
    private Boolean preservePlaybackPosition;
    private Integer issueContextSeconds;
    private BigDecimal defaultPlaybackVolume;
    private Integer defaultPageSize;
    private Boolean notifyOnTaskComplete;
    private Boolean autoOpenResultPage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
