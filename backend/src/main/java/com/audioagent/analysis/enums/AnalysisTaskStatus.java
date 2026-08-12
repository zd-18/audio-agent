package com.audioagent.analysis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum AnalysisTaskStatus {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    @EnumValue
    private final String code;
    private final String label;

    AnalysisTaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
