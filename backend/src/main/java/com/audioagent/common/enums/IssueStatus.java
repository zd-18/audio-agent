package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum IssueStatus {

    UNRESOLVED(0, "未处理"),
    SELECTED_FOR_REPAIR(1, "已选择修复"),
    IGNORED(2, "已忽略"),
    REPAIRED(3, "已修复");

    @EnumValue
    private final int code;
    private final String label;

    IssueStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
