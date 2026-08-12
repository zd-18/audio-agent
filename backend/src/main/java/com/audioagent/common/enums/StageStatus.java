package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum StageStatus {

    PENDING(0, "待执行"),
    RUNNING(1, "执行中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败"),
    SKIPPED(4, "跳过");

    @EnumValue
    private final int code;
    private final String label;

    StageStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
