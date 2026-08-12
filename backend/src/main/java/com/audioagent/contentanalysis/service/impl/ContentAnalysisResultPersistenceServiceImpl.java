package com.audioagent.contentanalysis.service.impl;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisResult;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.executor.ContentAnalysisExecution;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisResultMapper;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.prompt.ContentAnalysisPromptV1;
import com.audioagent.contentanalysis.service.ContentAnalysisResultPersistenceService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContentAnalysisResultPersistenceServiceImpl
        implements ContentAnalysisResultPersistenceService {

    private final AudioContentAnalysisResultMapper resultMapper;
    private final AudioContentAnalysisTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSuccess(AudioContentAnalysisTask task,
                            ContentAnalysisExecution execution) {
        LocalDateTime now = LocalDateTime.now();
        AudioContentAnalysisResult result =
                new AudioContentAnalysisResult();
        result.setId(IdWorker.getId());
        result.setUserId(task.getUserId());
        result.setTaskId(task.getId());
        result.setTranscriptId(task.getTranscriptId());
        result.setSummaryJson(json(
                execution.output().getSummary()));
        result.setKeyPointsJson(json(
                execution.output().getKeyPoints()));
        result.setChaptersJson(json(
                execution.output().getChapters()));
        result.setSpeechIssuesJson(json(
                execution.output().getSpeechIssues()));
        result.setPromptTokens(execution.promptTokens());
        result.setCompletionTokens(execution.completionTokens());
        result.setTotalTokens(execution.totalTokens());
        result.setModelName(execution.model());
        result.setPromptVersion(ContentAnalysisPromptV1.VERSION);
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        int affectedRows = resultMapper.upsert(result);
        if (affectedRows == 0
                && resultMapper.selectByTask(task.getId()) == null) {
            throw persistenceFailure(null);
        }
        if (taskMapper.complete(task.getId(), execution.model(), now)
                != 1) {
            throw persistenceFailure(null);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException | JsonProcessingException e) {
            throw persistenceFailure(e);
        }
    }

    private ContentAnalysisException persistenceFailure(Throwable cause) {
        return new ContentAnalysisException(
                ErrorCode.AI_RESULT_PERSISTENCE_FAILED, false,
                "智能分析结果保存失败，请联系管理员", cause);
    }
}
