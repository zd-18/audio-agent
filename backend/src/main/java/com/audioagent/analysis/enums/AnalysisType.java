package com.audioagent.analysis.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum AnalysisType {

    FULL("FULL", "完整分析"),

    /**
     * Internal persistence anchor for an Agent-authored ProcessingPlan.
     * It is not an audio diagnosis task and must never be dispatched to the
     * diagnosis executor or exposed by diagnosis task lists.
     */
    PROCESSING_CONTEXT("PROCESSING_CONTEXT", "处理上下文");

    @EnumValue
    private final String code;
    private final String label;

    AnalysisType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
