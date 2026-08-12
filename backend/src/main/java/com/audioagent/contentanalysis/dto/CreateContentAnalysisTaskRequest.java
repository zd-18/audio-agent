package com.audioagent.contentanalysis.dto;

import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.SummaryStyle;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Set;

@Data
public class CreateContentAnalysisTaskRequest {

    @Pattern(regexp = "^[1-9]\\d{0,18}$",
            message = "transcriptId 必须是有效的字符串 ID")
    private String transcriptId;

    private Set<AnalysisType> analysisTypes;
    private SummaryStyle summaryStyle;
}
