package com.audioagent.analysis.service;

import com.audioagent.analysis.vo.AudioAnalysisReportVO;

public interface AudioAnalysisReportService {

    AudioAnalysisReportVO getReport(Long taskId);

    /** 在 FULL 分析结果已保存、任务标记 SUCCESS 之前生成当前快照。 */
    AudioAnalysisReportVO generateAndSave(Long taskId, Long audioFileId);
}
