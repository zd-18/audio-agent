package com.audioagent.contentanalysis.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.chunk.SourceChunker;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.AnalysisTypeCodec;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.service.ContentAnalysisResultPersistenceService;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioContentAnalysisTaskExecutor {

    private final AudioContentAnalysisTaskMapper taskMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final AudioTranscriptSegmentMapper segmentMapper;
    private final AnalysisTypeCodec typeCodec;
    private final SourceChunker sourceChunker;
    private final ContentAnalysisEngine engine;
    private final ContentAnalysisResultPersistenceService persistenceService;

    public void execute(Long taskId) {
        AudioContentAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null
                || task.getStatus()
                != ContentAnalysisTaskStatus.RUNNING) {
            return;
        }
        long startedAt = System.nanoTime();
        updateProgress(taskId, 15);
        AudioTranscript transcript = transcriptMapper.selectOwned(
                task.getUserId(), task.getTranscriptId());
        if (transcript == null) {
            throw new ContentAnalysisException(
                    ErrorCode.TRANSCRIPT_NOT_FOUND, false,
                    "文字稿不存在");
        }
        if (!StringUtils.hasText(transcript.getFullText())) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_TRANSCRIPT_EMPTY, false,
                    "文字稿内容为空，无法进行智能分析");
        }
        var segments = segmentMapper.selectOwnedAll(
                task.getUserId(), task.getTranscriptId());
        updateProgress(taskId, 30);
        List<SourceChunk> chunks = sourceChunker.build(
                segments, transcript.getFullText(),
                transcript.getDurationMs());
        if (chunks.isEmpty()) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_TRANSCRIPT_EMPTY, false,
                    "文字稿内容为空，无法进行智能分析");
        }
        List<AnalysisType> analysisTypes =
                typeCodec.read(task.getAnalysisTypesJson(), task.getId());
        SummaryStyle style;
        try {
            style = SummaryStyle.valueOf(task.getSummaryStyle());
        } catch (RuntimeException e) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_ANALYSIS_FAILED, false,
                    "智能分析任务数据不完整", e);
        }
        updateProgress(taskId, 45);
        ContentAnalysisExecution execution = engine.analyze(
                Set.copyOf(analysisTypes), style,
                transcript.getLanguage(), transcript.getDurationMs(),
                chunks,
                new ContentAnalysisDiagnosticContext(
                        task.getId(),
                        task.getTranscriptId(),
                        task.getModelName(),
                        task.getPromptVersion()));
        updateProgress(taskId, 75);
        updateProgress(taskId, 90);
        persistenceService.saveSuccess(task, execution);
        log.info("Content analysis completed, taskId={}, transcriptId={}, "
                        + "userId={}, model={}, promptVersion={}, "
                        + "inputChars={}, sourceChunkCount={}, "
                        + "elapsedMs={}, totalTokens={}, keyPointCount={}, "
                        + "chapterCount={}, speechIssueCount={}",
                task.getId(), task.getTranscriptId(), task.getUserId(),
                execution.model(), task.getPromptVersion(),
                transcript.getFullText().length(), chunks.size(),
                (System.nanoTime() - startedAt) / 1_000_000,
                execution.totalTokens(),
                execution.output().getKeyPoints().size(),
                execution.output().getChapters().size(),
                execution.output().getSpeechIssues().size());
    }

    private void updateProgress(Long taskId, int progress) {
        if (taskMapper.updateProgress(taskId, progress,
                LocalDateTime.now()) != 1) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_ANALYSIS_FAILED, false,
                    "智能分析任务状态已变化");
        }
    }
}
