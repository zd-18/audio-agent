package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum TaskType {

    FULL_ANALYSIS(1, "完整分析"),
    TRANSCRIPT_ONLY(2, "仅转写"),
    QUALITY_CHECK_ONLY(3, "仅质量检测");

    @EnumValue
    private final int code;
    private final String label;

    TaskType(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
