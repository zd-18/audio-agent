package com.audioagent.contentanalysis.vo;

import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.model.AnalysisType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ContentAnalysisTaskVO {
    String taskId;
    String transcriptId;
    String status;
    List<AnalysisType> analysisTypes;
    Integer progressPercent;
    String modelName;
    Integer retryCount;
    String failureCode;
    String failureMessage;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static ContentAnalysisTaskVO from(
            AudioContentAnalysisTask task,
            List<AnalysisType> analysisTypes) {
        return ContentAnalysisTaskVO.builder()
                .taskId(task.getId().toString())
                .transcriptId(task.getTranscriptId().toString())
                .status(task.getStatus().name())
                .analysisTypes(analysisTypes)
                .progressPercent(task.getProgressPercent())
                .modelName(task.getModelName())
                .retryCount(task.getRetryCount())
                .failureCode(task.getFailureCode())
                .failureMessage(task.getFailureMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
