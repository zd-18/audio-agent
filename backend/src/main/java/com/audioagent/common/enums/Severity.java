package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum Severity {

    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    @EnumValue
    private final int code;
    private final String label;

    Severity(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
