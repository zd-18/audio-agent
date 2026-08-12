package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum Repairability {

    UNKNOWN(0, "未知"),
    AUTO_REPAIR(1, "自动修复"),
    MANUAL_CONFIRMATION(2, "人工确认"),
    SUGGEST_DELETE(3, "建议删除"),
    SUGGEST_RERECORD(4, "建议补录");

    @EnumValue
    private final int code;
    private final String label;

    Repairability(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
