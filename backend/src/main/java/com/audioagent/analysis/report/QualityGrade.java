package com.audioagent.analysis.report;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;

public enum QualityGrade {

    EXCELLENT("音频质量优秀"),
    GOOD("音频质量良好"),
    FAIR("音频质量一般"),
    POOR("建议优先处理主要问题");

    private final String displayText;

    QualityGrade(String displayText) {
        this.displayText = displayText;
    }

    public String getDisplayText() {
        return displayText;
    }

    public static QualityGrade fromScore(
            int score, AnalysisProperties.Grade thresholds) {
        if (score >= thresholds.getExcellentMin()) {
            return EXCELLENT;
        }
        if (score >= thresholds.getGoodMin()) {
            return GOOD;
        }
        if (score >= thresholds.getFairMin()) {
            return FAIR;
        }
        return POOR;
    }
}
