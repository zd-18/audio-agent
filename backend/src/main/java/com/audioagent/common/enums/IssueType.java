package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum IssueType {

    LONG_SILENCE("LONG_SILENCE", "长时间静音"),
    LOW_VOLUME("LOW_VOLUME", "音量过低"),
    HIGH_VOLUME("HIGH_VOLUME", "音量过高"),
    VOLUME_FLUCTUATION("VOLUME_FLUCTUATION", "音量波动"),
    NOISE("NOISE", "噪声"),
    CLIPPING("CLIPPING", "削波");

    @EnumValue
    private final String code;
    private final String label;

    IssueType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
