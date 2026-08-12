package com.audioagent.contentanalysis.prompt;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentAnalysisPromptV1Test {

    @Test
    void explicitlyForbidsParaphrasedOrCrossChunkEvidence() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setMaxEvidenceQuoteChars(88);
        ContentAnalysisPromptV1 prompt = new ContentAnalysisPromptV1(
                new ObjectMapper(), properties);

        String value = prompt.finalPrompt(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD,
                "zh",
                1_000L,
                List.of(new SourceChunk(
                        "S1-C1", 1, 0L, 1_000L,
                        "团队今天完成接口联调。",
                        TimePrecision.SEGMENT)));

        assertTrue(value.contains("不超过 88 个字符"));
        assertTrue(value.contains("逐字复制"));
        assertTrue(value.contains("单一 chunk"));
        assertTrue(value.contains("禁止概括、改写"));
        assertTrue(value.contains("正确示例"));
        assertTrue(value.contains("错误示例"));
        assertTrue(value.contains("找不到合适原文时应减少观点"));
        assertTrue(value.contains("\"summary\""));
        assertTrue(value.contains("\"speechIssues\""));
    }
}
