package com.audioagent.analysis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum AnalysisType {

    FULL("FULL", "完整分析");

    @EnumValue
    private final String code;
    private final String label;

    AnalysisType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
