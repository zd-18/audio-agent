package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum FileRole {

    ORIGINAL(1, "原始文件"),
    STANDARDIZED(2, "标准化音频"),
    ISSUE_CLIP(3, "问题片段"),
    REPAIR_RESULT(4, "修复结果"),
    FINAL_EXPORT(5, "最终导出");

    @EnumValue
    private final int code;
    private final String label;

    FileRole(int code, String label) {
        this.code = code;
        this.label = label;
    }
}
