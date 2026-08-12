package com.audioagent.analysis.processing;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.setting.model.UserProcessingPreferences;

import java.util.List;

public record ProcessingPlanContext(
        AudioAnalysisTask task,
        AudioAnalysisResult result,
        AudioAnalysisReportVO report,
        List<AudioIssueSegment> issues,
        UserProcessingPreferences preferences) {

    public ProcessingPlanContext(AudioAnalysisTask task,
                                 AudioAnalysisResult result,
                                 AudioAnalysisReportVO report,
                                 List<AudioIssueSegment> issues) {
        this(task, result, report, issues, null);
    }
}
