package com.audioagent.contentanalysis.service;

import com.audioagent.contentanalysis.dto.CreateContentAnalysisTaskRequest;
import com.audioagent.contentanalysis.vo.ContentAnalysisResultVO;
import com.audioagent.contentanalysis.vo.ContentAnalysisTaskVO;

public interface ContentAnalysisService {

    ContentAnalysisTaskVO create(
            Long userId, CreateContentAnalysisTaskRequest request);

    ContentAnalysisTaskVO get(Long userId, String taskId);

    ContentAnalysisResultVO getResult(Long userId, String taskId);

    ContentAnalysisTaskVO retry(Long userId, String taskId);
}
