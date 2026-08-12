package com.audioagent.contentanalysis.service.impl;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.dispatch.ContentAnalysisTaskDispatcher;
import com.audioagent.contentanalysis.dto.CreateContentAnalysisTaskRequest;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisResult;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisResultMapper;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.AnalysisTypeCodec;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.prompt.ContentAnalysisPromptV1;
import com.audioagent.contentanalysis.service.ContentAnalysisService;
import com.audioagent.contentanalysis.vo.ContentAnalysisResultVO;
import com.audioagent.contentanalysis.vo.ContentAnalysisTaskVO;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentAnalysisServiceImpl
        implements ContentAnalysisService {

    private static final int REUSE_WINDOW_MINUTES = 5;

    private final AudioContentAnalysisTaskMapper taskMapper;
    private final AudioContentAnalysisResultMapper resultMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final AudioFileMapper audioFileMapper;
    private final ContentAnalysisTaskDispatcher dispatcher;
    private final DeepSeekProperties properties;
    private final AnalysisTypeCodec typeCodec;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentAnalysisTaskVO create(
            Long userId, CreateContentAnalysisTaskRequest request) {
        requireConfigured();
        requirePositive(userId, "用户ID无效");
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "请求内容不能为空");
        }
        Long transcriptId = parseId(
                request.getTranscriptId(), "文字稿ID无效");
        AudioTranscript transcript = transcriptMapper
                .selectOwnedForUpdate(userId, transcriptId);
        if (transcript == null) {
            throw new BusinessException(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
        if (!StringUtils.hasText(transcript.getFullText())) {
            throw new BusinessException(ErrorCode.AI_TRANSCRIPT_EMPTY);
        }
        List<AnalysisType> types = typeCodec.normalize(
                request.getAnalysisTypes());
        String analysisTypesJson = typeCodec.write(Set.copyOf(types));
        SummaryStyle style = request.getSummaryStyle() == null
                ? SummaryStyle.STANDARD : request.getSummaryStyle();
        AudioContentAnalysisTask reusable =
                taskMapper.selectReusable(
                        userId, transcriptId, analysisTypesJson,
                        style.name(), LocalDateTime.now()
                                .minusMinutes(REUSE_WINDOW_MINUTES));
        if (reusable != null) {
            return toTaskVO(reusable);
        }

        LocalDateTime now = LocalDateTime.now();
        AudioContentAnalysisTask task =
                new AudioContentAnalysisTask();
        task.setUserId(userId);
        task.setTranscriptId(transcriptId);
        task.setStatus(ContentAnalysisTaskStatus.PENDING);
        task.setAnalysisTypesJson(analysisTypesJson);
        task.setSummaryStyle(style.name());
        task.setProgressPercent(0);
        task.setModelName(properties.getModel());
        task.setPromptVersion(ContentAnalysisPromptV1.VERSION);
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (taskMapper.insert(task) != 1 || task.getId() == null) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED,
                    "智能分析任务创建失败，请稍后重试");
        }
        dispatcher.dispatch(task.getId());
        return toTaskVO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentAnalysisTaskVO get(Long userId, String taskId) {
        requirePositive(userId, "用户ID无效");
        AudioContentAnalysisTask task = requireOwnedTask(
                userId, parseId(taskId, "智能分析任务ID无效"));
        return toTaskVO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentAnalysisResultVO getResult(
            Long userId, String taskId) {
        requirePositive(userId, "用户ID无效");
        Long id = parseId(taskId, "智能分析任务ID无效");
        AudioContentAnalysisTask task = requireOwnedTask(userId, id);
        if (task.getStatus() != ContentAnalysisTaskStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.AI_RESULT_NOT_FOUND,
                    "智能分析结果尚未生成");
        }
        AudioContentAnalysisResult result =
                resultMapper.selectOwnedByTask(userId, id);
        if (result == null) {
            throw new BusinessException(ErrorCode.AI_RESULT_NOT_FOUND);
        }
        AudioTranscript transcript = transcriptMapper.selectOwned(
                userId, result.getTranscriptId());
        if (transcript == null) {
            throw new BusinessException(ErrorCode.AI_RESULT_NOT_FOUND);
        }
        AudioFile audioFile = audioFileMapper.selectById(
                transcript.getAudioFileId());
        String audioFileName = audioFile != null
                && userId.equals(audioFile.getUserId())
                ? audioFile.getOriginalName() : "";
        try {
            return ContentAnalysisResultVO.builder()
                    .taskId(result.getTaskId().toString())
                    .transcriptId(result.getTranscriptId().toString())
                    .audioFileId(transcript.getAudioFileId().toString())
                    .audioFileName(audioFileName)
                    .modelName(result.getModelName())
                    .promptVersion(result.getPromptVersion())
                    .summary(objectMapper.readValue(
                            result.getSummaryJson(),
                            ContentAnalysisOutput.Summary.class))
                    .keyPoints(objectMapper.readValue(
                            result.getKeyPointsJson(),
                            new TypeReference<>() {
                            }))
                    .chapters(objectMapper.readValue(
                            result.getChaptersJson(),
                            new TypeReference<>() {
                            }))
                    .speechIssues(objectMapper.readValue(
                            result.getSpeechIssuesJson(),
                            new TypeReference<>() {
                            }))
                    .usage(ContentAnalysisResultVO.UsageVO.builder()
                            .promptTokens(result.getPromptTokens())
                            .completionTokens(
                                    result.getCompletionTokens())
                            .totalTokens(result.getTotalTokens())
                            .build())
                    .createdAt(result.getCreatedAt())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
                    "智能分析结果暂时无法读取");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentAnalysisTaskVO retry(Long userId, String taskId) {
        requireConfigured();
        Long id = parseId(taskId, "智能分析任务ID无效");
        AudioContentAnalysisTask task = requireOwnedTask(userId, id);
        if (task.getStatus() != ContentAnalysisTaskStatus.FAILED) {
            throw new BusinessException(ErrorCode.AI_TASK_NOT_RETRYABLE);
        }
        if (taskMapper.resetFailed(userId, id,
                LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.AI_TASK_NOT_RETRYABLE,
                    "任务状态已变化，请刷新后重试");
        }
        dispatcher.dispatch(id);
        return toTaskVO(requireOwnedTask(userId, id));
    }

    private AudioContentAnalysisTask requireOwnedTask(
            Long userId, Long taskId) {
        AudioContentAnalysisTask task =
                taskMapper.selectOwned(userId, taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AI_TASK_NOT_FOUND);
        }
        return task;
    }

    private ContentAnalysisTaskVO toTaskVO(
            AudioContentAnalysisTask task) {
        return ContentAnalysisTaskVO.from(
                task, typeCodec.read(
                        task.getAnalysisTypesJson(), task.getId()));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_NOT_CONFIGURED);
        }
    }

    private Long parseId(String value, String message) {
        if (!StringUtils.hasText(value)
                || !value.matches("[1-9]\\d{0,18}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }
}
