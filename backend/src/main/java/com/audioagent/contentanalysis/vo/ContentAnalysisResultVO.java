package com.audioagent.contentanalysis.vo;

import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ContentAnalysisResultVO {
    String taskId;
    String transcriptId;
    String audioFileId;
    String audioFileName;
    String modelName;
    String promptVersion;
    ContentAnalysisOutput.Summary summary;
    List<ContentAnalysisOutput.KeyPoint> keyPoints;
    List<ContentAnalysisOutput.Chapter> chapters;
    List<ContentAnalysisOutput.SpeechIssue> speechIssues;
    UsageVO usage;
    LocalDateTime createdAt;

    @Value
    @Builder
    public static class UsageVO {
        Integer promptTokens;
        Integer completionTokens;
        Integer totalTokens;
    }
}
