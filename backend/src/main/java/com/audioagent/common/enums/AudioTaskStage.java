package com.audioagent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum AudioTaskStage {

    PREPROCESS("PREPROCESS", "预处理"),
    TRANSCRIBE("TRANSCRIBE", "转写"),
    ANALYZE("ANALYZE", "分析"),
    REPORT("REPORT", "报告"),
    FINISHED("FINISHED", "已完成");

    @EnumValue
    private final String code;
    private final String label;

    AudioTaskStage(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
