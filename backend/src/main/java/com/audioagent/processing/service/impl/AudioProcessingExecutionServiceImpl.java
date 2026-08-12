package com.audioagent.processing.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.processing.ProcessingConfirmationStatus;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.common.api.PageResult;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.dispatch.AudioProcessingExecutionDispatcher;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.executor.ProcessingExecutionWorkDirectory;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.model.ProcessingExecutionStatus;
import com.audioagent.processing.model.ProcessingExecutionStepStatus;
import com.audioagent.processing.service.AudioProcessingExecutionService;
import com.audioagent.processing.snapshot.ProcessingExecutionSnapshot;
import com.audioagent.processing.snapshot.ProcessingExecutionSnapshotParser;
import com.audioagent.processing.vo.ProcessingExecutionVO;
import com.audioagent.processing.vo.ProcessingExecutionListVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioProcessingExecutionServiceImpl
        implements AudioProcessingExecutionService {

    private final AudioProcessingProperties properties;
    private final AudioProcessingConfirmationMapper confirmationMapper;
    private final AudioAnalysisTaskMapper taskMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioProcessingExecutionMapper executionMapper;
    private final AudioProcessingExecutionStepMapper executionStepMapper;
    private final ProcessingExecutionSnapshotParser snapshotParser;
    private final AudioProcessingExecutionDispatcher dispatcher;
    private final ProcessingExecutionWorkDirectory workDirectories;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProcessingExecutionListVO> list(
            Long userId, int current, int size, String status) {
        requirePositive(userId, "userId");
        if (current <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "页码必须大于 0");
        }
        if (size <= 0 || size > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "每页数量必须在 1～100 之间");
        }
        String normalizedStatus = normalizeStatus(status);
        IPage<ProcessingExecutionListVO> page = executionMapper
                .selectExecutionPage(new Page<>(current, size), userId,
                        normalizedStatus);
        return PageResult.of(page.getRecords(), page.getCurrent(),
                page.getSize(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingExecutionVO create(Long userId, Long confirmationId) {
        requireEnabled();
        requirePositive(userId, "userId");
        requirePositive(confirmationId, "confirmationId");

        AudioProcessingExecution existing = executionMapper
                .selectByConfirmationId(confirmationId);
        if (existing != null) {
            ensureOwned(userId, existing);
            return toVO(existing);
        }

        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectById(confirmationId);
        if (confirmation == null
                || !ProcessingConfirmationStatus.CONFIRMED.name().equals(
                confirmation.getConfirmationStatus())) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY);
        }
        if (confirmation.getAcceptedStepCount() == null
                || confirmation.getAcceptedStepCount() <= 0) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NO_ACCEPTED_STEPS);
        }

        AudioAnalysisTask task = taskMapper.selectById(
                confirmation.getTaskId());
        if (task == null || task.getStatus() != AnalysisTaskStatus.SUCCESS
                || !confirmation.getAudioFileId().equals(
                task.getAudioFileId())) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                    "Confirmation no longer belongs to a valid analysis task");
        }
        AudioFile sourceFile = audioFileMapper.selectById(
                confirmation.getAudioFileId());
        if (sourceFile == null
                || Integer.valueOf(1).equals(sourceFile.getDeleted())) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND);
        }
        if (!userId.equals(sourceFile.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }

        ProcessingExecutionSnapshot snapshot;
        try {
            snapshot = snapshotParser.parse(confirmation.getConfirmationJson());
        } catch (ProcessingExecutionException e) {
            throw new BusinessException(ErrorCode.valueOf(e.getFailureCode()),
                    e.getMessage());
        }
        validateSnapshotIdentity(confirmation, snapshot);
        if (snapshot.acceptedSteps().size()
                != confirmation.getAcceptedStepCount()) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                    "Accepted step count does not match the confirmed snapshot");
        }

        LocalDateTime now = LocalDateTime.now();
        AudioProcessingExecution execution = newExecution(userId,
                confirmation, snapshot, now);
        if (executionMapper.insertIgnore(execution) == 0) {
            AudioProcessingExecution concurrent = executionMapper
                    .selectByConfirmationId(confirmationId);
            if (concurrent == null) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_EXECUTION_FAILED,
                        "Processing execution could not be created");
            }
            ensureOwned(userId, concurrent);
            return toVO(concurrent);
        }

        List<AudioProcessingExecutionStep> steps = newSteps(
                execution.getId(), snapshot.acceptedSteps(), now);
        if (!steps.isEmpty()
                && executionStepMapper.insertBatch(steps) != steps.size()) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_FAILED,
                    "Execution steps were not fully persisted");
        }
        dispatchAfterCommit(execution.getId());
        log.info("Processing execution created, executionId={}, taskId={}, "
                        + "confirmationId={}, executableStepCount={}, "
                        + "skippedStepCount={}", execution.getId(),
                execution.getTaskId(), execution.getConfirmationId(),
                execution.getExecutableStepCount(),
                execution.getSkippedStepCount());
        return toVO(execution, steps);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingExecutionVO get(Long userId, Long executionId) {
        requirePositive(userId, "userId");
        requirePositive(executionId, "executionId");
        AudioProcessingExecution execution = load(executionId);
        ensureOwned(userId, execution);
        return toVO(execution);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingExecutionVO getByTask(Long userId, Long taskId) {
        requirePositive(userId, "userId");
        requirePositive(taskId, "taskId");
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND);
        }
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        if (file == null || !userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }
        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectLatestConfirmedByTaskId(taskId);
        if (confirmation == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_FOUND,
                    "No confirmed processing execution exists for this task");
        }
        AudioProcessingExecution execution = executionMapper
                .selectByConfirmationId(confirmation.getId());
        if (execution == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_FOUND);
        }
        return toVO(execution);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingExecutionVO retry(Long userId, Long executionId) {
        requireEnabled();
        requirePositive(userId, "userId");
        requirePositive(executionId, "executionId");
        AudioProcessingExecution execution = load(executionId);
        ensureOwned(userId, execution);
        if (!(ProcessingExecutionStatus.FAILED.name().equals(
                execution.getExecutionStatus())
                || ProcessingExecutionStatus.DEAD_LETTER.name().equals(
                execution.getExecutionStatus()))
                || execution.getResultFileId() != null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_RETRYABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (executionMapper.resetForManualRetry(executionId, now) != 1) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_RETRYABLE,
                    "Execution status changed; refresh and retry");
        }
        try {
            workDirectories.clean(executionId);
        } catch (ProcessingExecutionException e) {
            throw new BusinessException(ErrorCode.valueOf(e.getFailureCode()),
                    e.getMessage());
        }
        executionStepMapper.resetForRetry(executionId, now);
        dispatchAfterCommit(executionId);
        return toVO(load(executionId));
    }

    private AudioProcessingExecution newExecution(
            Long userId, AudioProcessingConfirmation confirmation,
            ProcessingExecutionSnapshot snapshot, LocalDateTime now) {
        AudioProcessingExecution execution =
                new AudioProcessingExecution();
        execution.setId(IdWorker.getId());
        execution.setUserId(userId);
        execution.setTaskId(confirmation.getTaskId());
        execution.setAudioFileId(confirmation.getAudioFileId());
        execution.setConfirmationId(confirmation.getId());
        execution.setSourcePlanId(confirmation.getPlanId());
        execution.setSourcePlanRevision(
                confirmation.getSourcePlanRevision());
        execution.setExecutionStatus(ProcessingExecutionStatus.PENDING.name());
        execution.setAcceptedStepCount(snapshot.acceptedSteps().size());
        execution.setExecutableStepCount(snapshot.acceptedSteps().size());
        execution.setSkippedStepCount(0);
        execution.setProgressPercent(0);
        execution.setRetryCount(0);
        execution.setMaxRetryCount(properties.getMaxRetryCount());
        execution.setCreatedAt(now);
        execution.setUpdatedAt(now);
        return execution;
    }

    private List<AudioProcessingExecutionStep> newSteps(
            Long executionId,
            List<ProcessingExecutionSnapshot.Step> accepted,
            LocalDateTime now) {
        List<AudioProcessingExecutionStep> result = new ArrayList<>();
        for (ProcessingExecutionSnapshot.Step source : accepted.stream()
                .sorted(Comparator.comparing(
                        ProcessingExecutionSnapshot.Step::stepOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList()) {
            AudioProcessingExecutionStep step =
                    new AudioProcessingExecutionStep();
            step.setId(IdWorker.getId());
            step.setExecutionId(executionId);
            step.setSourceStepConfirmationId(
                    source.stepConfirmationId());
            step.setSourceProcessingStepId(source.sourceStepId());
            step.setStepOrder(source.stepOrder());
            step.setOperationType(source.operationType());
            step.setExecutionStatus(
                    ProcessingExecutionStepStatus.PENDING.name());
            step.setStartMs(source.startMs());
            step.setEndMs(source.endMs());
            step.setEffectiveParametersJson(serialize(
                    source.effectiveParameters()));
            step.setSkipReason(null);
            step.setFinishedAt(null);
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            result.add(step);
        }
        return List.copyOf(result);
    }

    private void validateSnapshotIdentity(
            AudioProcessingConfirmation confirmation,
            ProcessingExecutionSnapshot snapshot) {
        if (!confirmation.getId().equals(snapshot.confirmationId())
                || !confirmation.getTaskId().equals(snapshot.taskId())
                || !confirmation.getAudioFileId().equals(
                snapshot.audioFileId())
                || !confirmation.getPlanId().equals(snapshot.planId())
                || !confirmation.getSourcePlanRevision().equals(
                snapshot.sourcePlanRevision())) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                    "Confirmed snapshot identity does not match its record");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    "Execution parameters cannot be serialized");
        }
    }

    private AudioProcessingExecution load(Long executionId) {
        AudioProcessingExecution execution = executionMapper
                .selectExecutionById(executionId);
        if (execution == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_NOT_FOUND);
        }
        return execution;
    }

    private void ensureOwned(Long userId,
                             AudioProcessingExecution execution) {
        if (!userId.equals(execution.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }
    }

    private ProcessingExecutionVO toVO(
            AudioProcessingExecution execution) {
        return toVO(execution, executionStepMapper.selectByExecutionId(
                execution.getId()));
    }

    private ProcessingExecutionVO toVO(
            AudioProcessingExecution execution,
            List<AudioProcessingExecutionStep> rawSteps) {
        List<ProcessingExecutionVO.Step> steps = rawSteps == null
                ? List.of() : rawSteps.stream().map(step ->
                ProcessingExecutionVO.Step.builder()
                        .executionStepId(step.getId())
                        .stepOrder(step.getStepOrder())
                        .operationType(step.getOperationType())
                        .executionStatus(step.getExecutionStatus())
                        .startMs(step.getStartMs())
                        .endMs(step.getEndMs())
                        .skipReason(step.getSkipReason())
                        .failureMessage(step.getFailureMessage())
                        .build()).toList();
        return ProcessingExecutionVO.builder()
                .executionId(execution.getId())
                .taskId(execution.getTaskId())
                .audioFileId(execution.getAudioFileId())
                .confirmationId(execution.getConfirmationId())
                .executionStatus(execution.getExecutionStatus())
                .currentStage(execution.getCurrentStage())
                .progressPercent(execution.getProgressPercent())
                .acceptedStepCount(execution.getAcceptedStepCount())
                .executableStepCount(execution.getExecutableStepCount())
                .skippedStepCount(execution.getSkippedStepCount())
                .retryCount(execution.getRetryCount())
                .resultFileId(execution.getResultFileId())
                .failureCode(execution.getFailureCode())
                .failureMessage(execution.getFailureMessage())
                .steps(steps)
                .startedAt(execution.getStartedAt())
                .finishedAt(execution.getFinishedAt())
                .createdAt(execution.getCreatedAt())
                .updatedAt(execution.getUpdatedAt())
                .build();
    }

    private void dispatchAfterCommit(Long executionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatcher.dispatch(executionId);
                        }
                    });
        } else {
            dispatcher.dispatch(executionId);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_EXECUTION_FAILED,
                    "Audio processing execution is disabled");
        }
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    name + " must be greater than 0");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProcessingExecutionStatus.valueOf(
                    status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "处理任务状态不合法");
        }
    }
}
