package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.dto.UpdateProcessingStepConfirmationRequest;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.analysis.entity.AudioProcessingStepConfirmation;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.mapper.AudioProcessingPlanMapper;
import com.audioagent.analysis.mapper.AudioProcessingStepConfirmationMapper;
import com.audioagent.analysis.mapper.AudioProcessingStepMapper;
import com.audioagent.analysis.processing.ProcessingConfirmationStatus;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.processing.ProcessingPlanStatus;
import com.audioagent.analysis.processing.ProcessingStepDecision;
import com.audioagent.analysis.service.AudioProcessingConfirmationService;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioProcessingConfirmationServiceImpl
        implements AudioProcessingConfirmationService {

    private final AudioAnalysisTaskMapper taskMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioProcessingPlanMapper planMapper;
    private final AudioProcessingStepMapper processingStepMapper;
    private final AudioProcessingConfirmationMapper confirmationMapper;
    private final AudioProcessingStepConfirmationMapper stepConfirmationMapper;
    private final ProcessingParameterValidator parameterValidator;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingConfirmationVO create(Long userId, Long taskId) {
        AudioAnalysisTask task = loadOwnedTask(userId, taskId);
        requireSuccessfulTask(task);
        AudioProcessingPlan plan = loadReadyPlanForUpdate(taskId);
        LocalDateTime now = LocalDateTime.now();
        confirmationMapper.markOldDraftsStale(plan.getId(),
                plan.getPlanRevision(), now);

        AudioProcessingConfirmation existing = confirmationMapper
                .selectByPlanRevision(plan.getId(), plan.getPlanRevision());
        if (existing != null) {
            return loadVO(existing, plan);
        }

        List<AudioProcessingStep> sourceSteps = safeSteps(
                processingStepMapper.selectByPlanId(plan.getId()));
        if (sourceSteps.size() != plan.getStepCount()) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_DATA_INCOMPLETE,
                    "Processing plan steps are incomplete");
        }

        AudioProcessingConfirmation confirmation = newConfirmation(
                task, plan, sourceSteps.size(), now);
        if (confirmationMapper.insertIgnore(confirmation) == 0) {
            AudioProcessingConfirmation concurrent = confirmationMapper
                    .selectByPlanRevision(plan.getId(), plan.getPlanRevision());
            if (concurrent == null) {
                throw new IllegalStateException(
                        "Processing confirmation was not created");
            }
            return loadVO(concurrent, plan);
        }

        List<AudioProcessingStepConfirmation> decisions = new ArrayList<>(
                sourceSteps.size());
        for (AudioProcessingStep source : sourceSteps) {
            Map<String, Object> original = parseMap(source.getParametersJson());
            AudioProcessingStepConfirmation decision =
                    new AudioProcessingStepConfirmation();
            decision.setId(IdWorker.getId());
            decision.setConfirmationId(confirmation.getId());
            decision.setSourceStepId(source.getId());
            decision.setDecision(ProcessingStepDecision.PENDING.name());
            decision.setUserConfirmed(false);
            decision.setParameterOverridesJson(serializeMap(Map.of()));
            decision.setEffectiveParametersJson(serializeMap(original));
            decision.setCreatedAt(now);
            decision.setUpdatedAt(now);
            decisions.add(decision);
        }
        if (!decisions.isEmpty()
                && stepConfirmationMapper.insertBatch(decisions)
                != decisions.size()) {
            throw new IllegalStateException(
                    "Processing confirmation steps were not fully created");
        }
        audit("CREATE", userId, confirmation);
        return toVO(confirmation, sourceSteps, decisions);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingConfirmationVO getCurrent(Long userId, Long taskId) {
        loadOwnedTask(userId, taskId);
        AudioProcessingPlan plan = loadReadyPlan(taskId);
        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectByPlanRevision(plan.getId(), plan.getPlanRevision());
        if (confirmation == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_NOT_FOUND);
        }
        return loadVO(confirmation, plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingConfirmationVO.Step updateStep(
            Long userId, Long confirmationId, Long stepConfirmationId,
            UpdateProcessingStepConfirmationRequest request) {
        requirePositive(confirmationId, "confirmationId");
        requirePositive(stepConfirmationId, "stepConfirmationId");
        AudioProcessingConfirmation confirmation = loadOwnedForUpdate(
                userId, confirmationId);
        AudioAnalysisTask task = taskMapper.selectById(
                confirmation.getTaskId());
        ensureDraft(confirmation);
        AudioProcessingPlan plan = requireCurrentPlan(confirmation, task);

        AudioProcessingStepConfirmation stepConfirmation =
                stepConfirmationMapper.selectOwnedStep(
                        confirmationId, stepConfirmationId);
        if (stepConfirmation == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_NOT_FOUND,
                    "Step confirmation does not belong to this confirmation");
        }
        AudioProcessingStep source = processingStepMapper.selectById(
                stepConfirmation.getSourceStepId());
        if (source == null || !plan.getId().equals(source.getPlanId())) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFIRMATION_STALE,
                    "Source processing step no longer exists");
        }

        ProcessingStepDecision decision = parseDecision(request.getDecision());
        Map<String, Object> submittedOverrides =
                request.getParameterOverrides() == null
                ? Map.of() : new LinkedHashMap<>(
                request.getParameterOverrides());
        Map<String, Object> original = parseMap(source.getParametersJson());
        Map<String, Object> effective = parameterValidator.mergeAndValidate(
                source, original, submittedOverrides);
        Map<String, Object> overrides = new LinkedHashMap<>(
                submittedOverrides);
        overrides.entrySet().removeIf(entry -> objectMapper.valueToTree(
                entry.getValue()).equals(objectMapper.valueToTree(
                original.get(entry.getKey()))));
        LocalDateTime now = LocalDateTime.now();
        stepConfirmation.setDecision(decision.name());
        stepConfirmation.setUserConfirmed(request.getUserConfirmed());
        stepConfirmation.setParameterOverridesJson(serializeMap(overrides));
        stepConfirmation.setEffectiveParametersJson(serializeMap(effective));
        stepConfirmation.setUserNote(normalizeNote(request.getUserNote()));
        stepConfirmation.setUpdatedAt(now);
        if (stepConfirmationMapper.updateById(stepConfirmation) != 1) {
            throw new IllegalStateException(
                    "Processing step confirmation was not updated");
        }

        List<AudioProcessingStepConfirmation> all = safeDecisions(
                stepConfirmationMapper.selectByConfirmationId(confirmationId));
        Counts counts = counts(all);
        if (confirmationMapper.updateCounts(confirmationId,
                counts.accepted(), counts.rejected(), counts.pending(), now)
                != 1) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_NOT_EDITABLE);
        }
        confirmation.setAcceptedStepCount(counts.accepted());
        confirmation.setRejectedStepCount(counts.rejected());
        confirmation.setPendingStepCount(counts.pending());
        confirmation.setUpdatedAt(now);
        audit("UPDATE_STEP", userId, confirmation);
        return toStepVO(stepConfirmation, source);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingConfirmationVO confirm(Long userId,
                                             Long confirmationId) {
        requirePositive(confirmationId, "confirmationId");
        AudioProcessingConfirmation confirmation = loadOwnedForUpdate(
                userId, confirmationId);
        AudioAnalysisTask task = taskMapper.selectById(
                confirmation.getTaskId());
        ensureDraft(confirmation);
        requireSuccessfulTask(task);
        AudioProcessingPlan plan = requireCurrentPlan(confirmation, task);
        List<AudioProcessingStep> sources = safeSteps(
                processingStepMapper.selectByPlanId(plan.getId()));
        List<AudioProcessingStepConfirmation> decisions = safeDecisions(
                stepConfirmationMapper.selectByConfirmationId(confirmationId));
        validateFinalDecisions(sources, decisions);

        Counts counts = counts(decisions);
        if (counts.pending() > 0) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_HAS_PENDING_STEPS);
        }
        LocalDateTime now = LocalDateTime.now();
        confirmation.setConfirmationStatus(
                ProcessingConfirmationStatus.CONFIRMED.name());
        confirmation.setAcceptedStepCount(counts.accepted());
        confirmation.setRejectedStepCount(counts.rejected());
        confirmation.setPendingStepCount(0);
        confirmation.setConfirmedAt(now);
        confirmation.setUpdatedAt(now);
        ProcessingConfirmationVO result = toVO(
                confirmation, sources, decisions);
        String snapshot = serializeSnapshot(result);
        if (confirmationMapper.confirmDraft(
                confirmationId, snapshot, now) != 1) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_ALREADY_CONFIRMED);
        }
        confirmation.setConfirmationJson(snapshot);
        audit("CONFIRM", userId, confirmation);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingConfirmationVO cancel(Long userId,
                                            Long confirmationId) {
        requirePositive(confirmationId, "confirmationId");
        AudioProcessingConfirmation confirmation = loadOwnedForUpdate(
                userId, confirmationId);
        AudioAnalysisTask task = taskMapper.selectById(
                confirmation.getTaskId());
        ensureDraft(confirmation);
        AudioProcessingPlan plan = requireCurrentPlan(confirmation, task);
        LocalDateTime now = LocalDateTime.now();
        if (confirmationMapper.cancelDraft(confirmationId, now) != 1) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_NOT_EDITABLE);
        }
        confirmation.setConfirmationStatus(
                ProcessingConfirmationStatus.CANCELLED.name());
        confirmation.setUpdatedAt(now);
        audit("CANCEL", userId, confirmation);
        return loadVO(confirmation, plan);
    }

    private void validateFinalDecisions(
            List<AudioProcessingStep> sources,
            List<AudioProcessingStepConfirmation> decisions) {
        Map<Long, AudioProcessingStep> sourceMap = sources.stream()
                .collect(Collectors.toMap(AudioProcessingStep::getId,
                        Function.identity()));
        if (sources.size() != decisions.size()) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_STALE,
                    "Source processing steps have changed");
        }
        for (AudioProcessingStepConfirmation decision : decisions) {
            AudioProcessingStep source = sourceMap.get(
                    decision.getSourceStepId());
            if (source == null) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_CONFIRMATION_STALE,
                        "Source processing step no longer exists");
            }
            ProcessingStepDecision value = parseDecision(
                    decision.getDecision());
            requireExecutableIfAccepted(source, value);
            if (value == ProcessingStepDecision.PENDING) {
                continue;
            }
            if (value == ProcessingStepDecision.ACCEPTED
                    && Boolean.TRUE.equals(source.getRequiresConfirmation())
                    && !Boolean.TRUE.equals(decision.getUserConfirmed())) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_STEP_CONFIRMATION_REQUIRED,
                        "Accepted step requires explicit user confirmation");
            }
            Map<String, Object> original = parseMap(
                    source.getParametersJson());
            Map<String, Object> overrides = parseMap(
                    decision.getParameterOverridesJson());
            Map<String, Object> expected = parameterValidator
                    .mergeAndValidate(source, original, overrides);
            Map<String, Object> effective = parseMap(
                    decision.getEffectiveParametersJson());
            if (!objectMapper.valueToTree(expected).equals(
                    objectMapper.valueToTree(effective))) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_PARAMETER_INVALID,
                        "Effective parameters do not match the approved overrides");
            }
        }
    }

    private ProcessingConfirmationVO loadVO(
            AudioProcessingConfirmation confirmation,
            AudioProcessingPlan plan) {
        return toVO(confirmation,
                safeSteps(processingStepMapper.selectByPlanId(plan.getId())),
                safeDecisions(stepConfirmationMapper.selectByConfirmationId(
                        confirmation.getId())));
    }

    private void requireExecutableIfAccepted(
            AudioProcessingStep source, ProcessingStepDecision decision) {
        if (decision != ProcessingStepDecision.ACCEPTED) {
            return;
        }
        ProcessingOperationType operation;
        try {
            operation = ProcessingOperationType.valueOf(
                    source.getOperationType());
        } catch (Exception e) {
            throw legacyOperation(source.getOperationType());
        }
        if (!operation.isExecutable()) {
            throw legacyOperation(operation.name());
        }
    }

    private BusinessException legacyOperation(String operation) {
        return new BusinessException(ErrorCode.PROCESSING_CONFIRMATION_STALE,
                "Processing operation " + operation
                        + " is not executable in this stage; regenerate the plan");
    }

    private ProcessingConfirmationVO toVO(
            AudioProcessingConfirmation confirmation,
            List<AudioProcessingStep> sources,
            List<AudioProcessingStepConfirmation> decisions) {
        Map<Long, AudioProcessingStep> sourceMap = sources.stream()
                .collect(Collectors.toMap(AudioProcessingStep::getId,
                        Function.identity(), (left, right) -> left));
        List<ProcessingConfirmationVO.Step> steps = decisions.stream()
                .sorted(Comparator.comparingInt(decision -> {
                    AudioProcessingStep source = sourceMap.get(
                            decision.getSourceStepId());
                    return source == null || source.getStepOrder() == null
                            ? Integer.MAX_VALUE : source.getStepOrder();
                }))
                .map(decision -> toStepVO(decision,
                        sourceMap.get(decision.getSourceStepId())))
                .toList();
        String resultMessage = ProcessingConfirmationStatus.CONFIRMED.name()
                .equals(confirmation.getConfirmationStatus())
                && Integer.valueOf(0).equals(
                confirmation.getAcceptedStepCount())
                ? "User confirmed that no suggested steps should be executed."
                : null;
        return ProcessingConfirmationVO.builder()
                .confirmationId(confirmation.getId())
                .taskId(confirmation.getTaskId())
                .audioFileId(confirmation.getAudioFileId())
                .planId(confirmation.getPlanId())
                .sourcePlanRevision(confirmation.getSourcePlanRevision())
                .confirmationStatus(confirmation.getConfirmationStatus())
                .acceptedStepCount(confirmation.getAcceptedStepCount())
                .rejectedStepCount(confirmation.getRejectedStepCount())
                .pendingStepCount(confirmation.getPendingStepCount())
                .resultMessage(resultMessage)
                .steps(steps)
                .confirmedAt(confirmation.getConfirmedAt())
                .createdAt(confirmation.getCreatedAt())
                .updatedAt(confirmation.getUpdatedAt())
                .build();
    }

    private ProcessingConfirmationVO.Step toStepVO(
            AudioProcessingStepConfirmation decision,
            AudioProcessingStep source) {
        if (source == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_STALE,
                    "Source processing step no longer exists");
        }
        return ProcessingConfirmationVO.Step.builder()
                .stepConfirmationId(decision.getId())
                .sourceStepId(decision.getSourceStepId())
                .stepOrder(source.getStepOrder())
                .operationType(source.getOperationType())
                .title(source.getTitle())
                .decision(decision.getDecision())
                .userConfirmed(decision.getUserConfirmed())
                .requiresConfirmation(source.getRequiresConfirmation())
                .startMs(source.getStartMs())
                .endMs(source.getEndMs())
                .originalParameters(parseMap(source.getParametersJson()))
                .parameterOverrides(parseMap(
                        decision.getParameterOverridesJson()))
                .effectiveParameters(parseMap(
                        decision.getEffectiveParametersJson()))
                .userNote(decision.getUserNote())
                .build();
    }

    private AudioProcessingConfirmation newConfirmation(
            AudioAnalysisTask task, AudioProcessingPlan plan,
            int stepCount, LocalDateTime now) {
        AudioProcessingConfirmation confirmation =
                new AudioProcessingConfirmation();
        confirmation.setId(IdWorker.getId());
        confirmation.setTaskId(task.getId());
        confirmation.setAudioFileId(task.getAudioFileId());
        confirmation.setPlanId(plan.getId());
        confirmation.setSourcePlanRevision(plan.getPlanRevision());
        confirmation.setConfirmationStatus(
                ProcessingConfirmationStatus.DRAFT.name());
        confirmation.setAcceptedStepCount(0);
        confirmation.setRejectedStepCount(0);
        confirmation.setPendingStepCount(stepCount);
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        return confirmation;
    }

    private AudioProcessingConfirmation loadOwnedForUpdate(Long userId,
                                                            Long id) {
        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectByIdForUpdate(id);
        if (confirmation == null) {
            throw confirmationNotFound();
        }
        AudioAnalysisTask task;
        try {
            task = loadOwnedTask(userId,
                confirmation.getTaskId());
        } catch (BusinessException ignored) {
            throw confirmationNotFound();
        }
        if (!task.getAudioFileId().equals(confirmation.getAudioFileId())) {
            throw confirmationNotFound();
        }
        return confirmation;
    }

    private BusinessException confirmationNotFound() {
        return new BusinessException(
                ErrorCode.PROCESSING_CONFIRMATION_NOT_FOUND,
                "资源不存在或不可访问");
    }

    private AudioAnalysisTask loadOwnedTask(Long userId, Long taskId) {
        requirePositive(userId, "userId");
        requirePositive(taskId, "taskId");
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "资源不存在或不可访问");
        }
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        if (file == null || !userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "资源不存在或不可访问");
        }
        return task;
    }

    private AudioProcessingPlan loadReadyPlan(Long taskId) {
        AudioProcessingPlan plan = planMapper.selectByTaskId(taskId);
        return requireReadyPlan(plan);
    }

    private AudioProcessingPlan loadReadyPlanForUpdate(Long taskId) {
        AudioProcessingPlan plan = planMapper.selectByTaskIdForUpdate(taskId);
        return requireReadyPlan(plan);
    }

    private AudioProcessingPlan requireReadyPlan(AudioProcessingPlan plan) {
        if (plan == null || plan.getPlanRevision() == null
                || !ProcessingPlanStatus.READY.name().equals(
                plan.getPlanStatus())) {
            throw new BusinessException(ErrorCode.PROCESSING_PLAN_NOT_READY,
                    "A READY processing plan is required");
        }
        return plan;
    }

    private AudioProcessingPlan requireCurrentPlan(
            AudioProcessingConfirmation confirmation,
            AudioAnalysisTask task) {
        AudioProcessingPlan plan = planMapper.selectByTaskId(task.getId());
        if (plan == null || !plan.getId().equals(confirmation.getPlanId())
                || !plan.getPlanRevision().equals(
                confirmation.getSourcePlanRevision())) {
            throw new BusinessException(ErrorCode.PROCESSING_CONFIRMATION_STALE);
        }
        return plan;
    }

    private void requireSuccessfulTask(AudioAnalysisTask task) {
        if (task.getStatus() != AnalysisTaskStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PROCESSING_PLAN_NOT_READY,
                    "Only a successful analysis task can be confirmed");
        }
    }

    private void ensureDraft(AudioProcessingConfirmation confirmation) {
        ProcessingConfirmationStatus status;
        try {
            status = ProcessingConfirmationStatus.valueOf(
                    confirmation.getConfirmationStatus());
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_NOT_EDITABLE);
        }
        switch (status) {
            case DRAFT -> {
                return;
            }
            case CONFIRMED -> throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_ALREADY_CONFIRMED);
            case STALE -> throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_STALE);
            case CANCELLED -> throw new BusinessException(
                    ErrorCode.PROCESSING_CONFIRMATION_CANCELLED);
        }
    }

    private ProcessingStepDecision parseDecision(String value) {
        try {
            return ProcessingStepDecision.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "decision must be PENDING, ACCEPTED or REJECTED");
        }
    }

    private Counts counts(List<AudioProcessingStepConfirmation> decisions) {
        int accepted = 0;
        int rejected = 0;
        int pending = 0;
        for (AudioProcessingStepConfirmation decision : decisions) {
            ProcessingStepDecision value = parseDecision(
                    decision.getDecision());
            switch (value) {
                case ACCEPTED -> accepted++;
                case REJECTED -> rejected++;
                case PENDING -> pending++;
            }
        }
        return new Counts(accepted, rejected, pending);
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(
                    json, new TypeReference<>() {
                    });
            return value == null ? Map.of() : value;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PARAMETER_INVALID,
                    "Stored processing parameters are invalid");
        }
    }

    private String serializeMap(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters == null
                    ? Map.of() : parameters);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PARAMETER_INVALID,
                    "Processing parameters cannot be serialized");
        }
    }

    private String serializeSnapshot(ProcessingConfirmationVO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Processing confirmation snapshot cannot be serialized", e);
        }
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private List<AudioProcessingStep> safeSteps(
            List<AudioProcessingStep> steps) {
        return steps == null ? List.of() : steps;
    }

    private List<AudioProcessingStepConfirmation> safeDecisions(
            List<AudioProcessingStepConfirmation> decisions) {
        return decisions == null ? List.of() : decisions;
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    name + " must be greater than 0");
        }
    }

    private void audit(String operation, Long userId,
                       AudioProcessingConfirmation confirmation) {
        log.info("Processing confirmation operation={}, userId={}, taskId={}, "
                        + "planId={}, planRevision={}, confirmationId={}, "
                        + "acceptedStepCount={}, rejectedStepCount={}, "
                        + "pendingStepCount={}, confirmationStatus={}",
                operation, userId, confirmation.getTaskId(),
                confirmation.getPlanId(),
                confirmation.getSourcePlanRevision(), confirmation.getId(),
                confirmation.getAcceptedStepCount(),
                confirmation.getRejectedStepCount(),
                confirmation.getPendingStepCount(),
                confirmation.getConfirmationStatus());
    }

    private record Counts(int accepted, int rejected, int pending) {
    }
}
