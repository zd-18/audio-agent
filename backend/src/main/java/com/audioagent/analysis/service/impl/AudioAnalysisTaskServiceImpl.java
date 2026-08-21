package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.dto.CreateTaskRequest;
import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.enums.AnalysisType;
import com.audioagent.analysis.event.AudioAnalysisTaskCreatedEvent;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.outbox.AudioAnalysisTaskDispatchOutboxService;
import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.analysis.vo.AudioAnalysisResultVO;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioAnalysisTaskServiceImpl implements AudioAnalysisTaskService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AudioAnalysisTaskMapper audioAnalysisTaskMapper;
    private final AudioAnalysisResultMapper audioAnalysisResultMapper;
    private final AudioFileMapper audioFileMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final LoudnessEvaluator loudnessEvaluator;
    private final AudioResourceOwnershipService ownershipService;
    private final AudioAnalysisTaskDispatchOutboxService dispatchOutboxService;
    private final AnalysisProperties analysisProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResult<TaskListVO> listTasks(Long userId, int current, int size,
                                            String status, Long audioFileId,
                                            String analysisType,
                                            String keyword) {
        requireUserId(userId);
        validatePage(current, size);
        if (audioFileId != null && audioFileId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "audioFileId must be greater than 0");
        }

        String normalizedStatus = normalizeTaskStatus(status);
        String normalizedType = normalizeAnalysisType(analysisType);
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim()
                : null;
        IPage<TaskListVO> page = audioAnalysisTaskMapper.selectTaskPage(
                new Page<>(current, size), userId, normalizedStatus, audioFileId,
                normalizedType, normalizedKeyword);

        log.info("Analysis task page queried, userId={}, current={}, size={}, status={}, audioFileId={}, analysisType={}, keyword={}, total={}",
                userId, current, size, normalizedStatus, audioFileId,
                normalizedType, normalizedKeyword, page.getTotal());
        return PageResult.of(page.getRecords(), page.getCurrent(),
                page.getSize(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO createTask(Long userId, CreateTaskRequest request) {
        requireUserId(userId);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "请求内容不能为空");
        }
        ownershipService.requireFileOwned(userId, request.getAudioFileId());
        return createTaskInternal(request, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO createTaskFromUploadedFile(Long audioFileId, Long userId,
                                             Long sourceEventId) {
        requireUserId(userId);
        if (sourceEventId == null || sourceEventId <= 0) {
            throw new IllegalArgumentException(
                    "sourceEventId must be greater than 0");
        }
        AudioAnalysisTask existing =
                audioAnalysisTaskMapper.selectBySourceEventId(sourceEventId);
        if (existing != null) {
            return TaskVO.from(existing);
        }
        CreateTaskRequest request = new CreateTaskRequest();
        request.setAudioFileId(audioFileId);
        request.setAnalysisType(AnalysisType.FULL.name());
        return createTaskInternal(request, sourceEventId, userId);
    }

    private TaskVO createTaskInternal(CreateTaskRequest request,
                                      Long sourceEventId,
                                      Long expectedUserId) {
        Long audioFileId = request.getAudioFileId();

        AudioFile audioFile = audioFileMapper.selectById(audioFileId);
        if (audioFile == null
                || Integer.valueOf(1).equals(audioFile.getDeleted())) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_NOT_FOUND,
                    "音频文件不存在"
            );
        }
        if (expectedUserId != null
                && !expectedUserId.equals(audioFile.getUserId())) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_ACCESS_DENIED,
                    "Uploaded audio file does not belong to event user");
        }

        AnalysisType analysisType = resolveAnalysisType(
                request.getAnalysisType()
        );

        LocalDateTime now = LocalDateTime.now();

        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setAudioFileId(audioFileId);
        task.setSourceEventId(sourceEventId);
        task.setAnalysisType(analysisType);
        task.setStatus(AnalysisTaskStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        int insertedRows = audioAnalysisTaskMapper.insert(task);

        if (insertedRows != 1 || task.getId() == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "任务创建失败"
            );
        }

        log.info(
                "Analysis task created, taskId={}, audioFileId={}, analysisType={}",
                task.getId(), audioFileId, analysisType.name()
        );

        ensureInitialDispatch(task);

        return TaskVO.from(task);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskVO getTaskDetail(Long userId, Long taskId) {
        requireUserId(userId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID, "任务ID不能为空");
        }

        ownershipService.requireTaskOwned(userId, taskId);
        AudioAnalysisTask task =
                audioAnalysisTaskMapper.selectById(taskId);

        if (task == null) {
            throw new BusinessException(
                    ErrorCode.AUDIO_TASK_NOT_FOUND, "分析任务不存在");
        }

        AudioAnalysisResult result =
                audioAnalysisResultMapper.selectOne(
                        new LambdaQueryWrapper<AudioAnalysisResult>()
                                .eq(AudioAnalysisResult::getTaskId,
                                        taskId)
                );

        return buildTaskVO(task, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO retryTask(Long userId, Long taskId) {
        requireUserId(userId);
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID, "任务ID不能为空");
        }

        ownershipService.requireTaskOwned(userId, taskId);
        AudioAnalysisTask task =
                audioAnalysisTaskMapper.selectById(taskId);

        if (task == null) {
            throw new BusinessException(
                    ErrorCode.AUDIO_TASK_NOT_FOUND, "分析任务不存在");
        }

        if (task.getStatus() != AnalysisTaskStatus.FAILED) {
            throw new BusinessException(
                    ErrorCode.AUDIO_TASK_STATUS_INVALID,
                    "只有失败的任务才允许重试，当前状态: "
                            + task.getStatus().name()
            );
        }

        /*
         * 原子条件更新：只有 FAILED 状态才能重置为 PENDING。
         */
        LambdaUpdateWrapper<AudioAnalysisTask> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(AudioAnalysisTask::getId, taskId)
                .eq(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.FAILED)
                .set(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PENDING)
                .set(AudioAnalysisTask::getProgress, 0)
                .set(AudioAnalysisTask::getRetryCount, 0)
                .set(AudioAnalysisTask::getNextRetryAt, null)
                .set(AudioAnalysisTask::getErrorMessage, null)
                .set(AudioAnalysisTask::getLastErrorCode, null)
                .set(AudioAnalysisTask::getLastMessageId, null)
                .set(AudioAnalysisTask::getStartedAt, null)
                .set(AudioAnalysisTask::getFinishedAt, null)
                .set(AudioAnalysisTask::getUpdatedAt,
                        LocalDateTime.now());

        int rows = audioAnalysisTaskMapper.update(null, wrapper);

        if (rows != 1) {
            throw new BusinessException(
                    ErrorCode.AUDIO_TASK_STATUS_INVALID,
                    "任务状态已变更，请刷新后重试"
            );
        }

        task = audioAnalysisTaskMapper.selectById(taskId);

        log.info("Task retry requested, taskId={}", taskId);

        dispatchManualRetry(task);

        return TaskVO.from(task);
    }

    private void ensureInitialDispatch(AudioAnalysisTask task) {
        if (isRabbitDispatch()) {
            dispatchOutboxService.createInitialDispatch(task.getId());
            return;
        }
        eventPublisher.publishEvent(new AudioAnalysisTaskCreatedEvent(
                this, task.getId(), task.getAudioFileId()));
    }

    private void dispatchManualRetry(AudioAnalysisTask task) {
        if (isRabbitDispatch()) {
            dispatchOutboxService.reactivateForManualRetry(task.getId());
            return;
        }
        eventPublisher.publishEvent(new AudioAnalysisTaskCreatedEvent(
                this, task.getId(), task.getAudioFileId()));
    }

    private boolean isRabbitDispatch() {
        return "rabbit".equalsIgnoreCase(
                analysisProperties.getDispatchMode());
    }

    private TaskVO buildTaskVO(AudioAnalysisTask task,
                               AudioAnalysisResult result) {
        TaskVO.TaskVOBuilder builder = TaskVO.builder()
                .taskId(task.getId())
                .audioFileId(task.getAudioFileId())
                .analysisType(
                        task.getAnalysisType() == null
                                ? null
                                : task.getAnalysisType().name()
                )
                .status(
                        task.getStatus() == null
                                ? null
                                : task.getStatus().name()
                )
                .progress(task.getProgress())
                .errorMessage(task.getErrorMessage())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .nextRetryAt(task.getNextRetryAt())
                .lastErrorCode(task.getLastErrorCode())
                .lastMessageId(task.getLastMessageId())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt());

        if (result != null) {
            builder.result(AudioAnalysisResultVO.from(
                    result, loudnessEvaluator));
        }

        return builder.build();
    }

    private AnalysisType resolveAnalysisType(String analysisType) {
        if (!StringUtils.hasText(analysisType)) {
            return AnalysisType.FULL;
        }
        try {
            AnalysisType resolved = AnalysisType.valueOf(
                    analysisType.toUpperCase());
            if (resolved != AnalysisType.FULL) {
                throw new IllegalArgumentException();
            }
            return resolved;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "不支持的分析类型: " + analysisType
            );
        }
    }

    private void validatePage(int current, int size) {
        if (current < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "current must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "size must be between 1 and 100");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
    }

    private String normalizeTaskStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            AnalysisTaskStatus.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "Unsupported task status: " + status);
        }
    }

    private String normalizeAnalysisType(String analysisType) {
        if (!StringUtils.hasText(analysisType)) {
            return null;
        }
        String normalized = analysisType.trim().toUpperCase(Locale.ROOT);
        try {
            if (AnalysisType.valueOf(normalized) != AnalysisType.FULL) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "Unsupported analysis type: " + analysisType);
        }
    }
}
