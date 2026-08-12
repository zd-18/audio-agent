package com.audioagent.contentanalysis.service;

import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.executor.ContentAnalysisExecution;

public interface ContentAnalysisResultPersistenceService {

    void saveSuccess(AudioContentAnalysisTask task,
                     ContentAnalysisExecution execution);
}
