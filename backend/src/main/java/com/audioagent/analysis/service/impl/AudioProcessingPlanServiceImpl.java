package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.mapper.AudioProcessingPlanMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.mapper.AudioProcessingStepMapper;
import com.audioagent.analysis.processing.ProcessingPlanContext;
import com.audioagent.analysis.processing.ProcessingPlanDraft;
import com.audioagent.analysis.processing.ProcessingPlanGenerator;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingStepDraft;
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.setting.config.UserSettingDefaults;
import com.audioagent.setting.model.UserProcessingPreferences;
import com.audioagent.setting.service.UserSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioProcessingPlanServiceImpl
        implements AudioProcessingPlanService {

    private final AudioAnalysisTaskMapper taskMapper;
    private final AudioAnalysisResultMapper resultMapper;
    private final AudioIssueSegmentMapper issueMapper;
    private final AudioProcessingPlanMapper planMapper;
    private final AudioProcessingStepMapper stepMapper;
    private final AudioProcessingConfirmationMapper confirmationMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisReportService reportService;
    private final ProcessingPlanGenerator planGenerator;
    private final ProcessingParameterValidator parameterValidator;
    private final UserSettingService userSettingService;
    private final AnalysisProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingPlanVO saveAgentPlanForOwner(
            Long userId, Long taskId, ProcessingPlanDraft draft) {
        validateTaskId(taskId);
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "userId must be greater than 0");
        }
        if (!properties.getProcessingPlan().isEnabled()) {
            throw new BusinessException(ErrorCode.PROCESSING_PLAN_NOT_READY,
                    "Audio processing plans are disabled");
        }
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND);
        }
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        if (task.getStatus() != AnalysisTaskStatus.SUCCESS || file == null
                || !userId.equals(file.getUserId())
                || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }
        validateExecutableOperations(draft);
        validateDraftParameters(draft);
        return save(task, draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingPlanVO generate(Long taskId) {
        UserProcessingPreferences preferences = userSettingService
                .getCurrentProcessingPreferences();
        return generate(taskId, preferences);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessingPlanVO generateForOwner(Long userId, Long taskId) {
        UserProcessingPreferences preferences = userSettingService
                .getProcessingPreferencesForOwner(userId);
        return generate(taskId, preferences);
    }

    private ProcessingPlanVO generate(
            Long taskId, UserProcessingPreferences preferences) {
        validateTaskId(taskId);
        if (!properties.getProcessingPlan().isEnabled()) {
            throw new BusinessException(ErrorCode.PROCESSING_PLAN_NOT_READY,
                    "处理方案功能当前未启用");
        }
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "分析任务不存在");
        }
        if (task.getStatus() != AnalysisTaskStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PROCESSING_PLAN_NOT_READY,
                    "只有分析成功的任务才能生成处理方案");
        }

        long startedAt = System.currentTimeMillis();
        try {
            AudioAnalysisReportVO report = loadReport(taskId);
            AudioAnalysisResult result = resultMapper.selectOne(
                    new LambdaQueryWrapper<AudioAnalysisResult>()
                            .eq(AudioAnalysisResult::getTaskId, taskId));
            if (result == null
                    || !task.getAudioFileId().equals(
                    result.getAudioFileId())) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_PLAN_DATA_INCOMPLETE,
                        "生成处理方案所需的分析结果不完整");
            }
            List<AudioIssueSegment> issues = issueMapper.selectByTask(taskId);
            if (preferences == null) {
                preferences = UserSettingDefaults.processingPreferences();
            }
            ProcessingPlanDraft draft = planGenerator.generate(
                    new ProcessingPlanContext(task, result, report,
                            issues == null ? List.of() : issues,
                            preferences));
            validateExecutableOperations(draft);
            ProcessingPlanVO saved = save(task, draft);
            long highCount = saved.getSteps().stream()
                    .filter(step -> "HIGH".equals(step.getPriority()))
                    .count();
            long confirmationCount = saved.getSteps().stream()
                    .filter(step -> Boolean.TRUE.equals(
                            step.getRequiresConfirmation())).count();
            log.info("Processing plan generated, taskId={}, audioFileId={}, "
                            + "planId={}, stepCount={}, highPriorityCount={}, "
                            + "requiresConfirmationCount={}, planVersion={}, "
                            + "elapsedMs={}",
                    taskId, task.getAudioFileId(), saved.getPlanId(),
                    saved.getStepCount(), highCount, confirmationCount,
                    saved.getPlanVersion(),
                    System.currentTimeMillis() - startedAt);
            return saved;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Processing plan generation failed, taskId={}, "
                    + "audioFileId={}", taskId, task.getAudioFileId(), e);
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_GENERATION_FAILED,
                    "处理方案生成失败，请稍后重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingPlanVO get(Long taskId) {
        validateTaskId(taskId);
        if (taskMapper.selectById(taskId) == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "分析任务不存在");
        }
        AudioProcessingPlan plan = planMapper.selectByTaskId(taskId);
        if (plan == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_NOT_FOUND,
                    "该任务尚未生成处理方案");
        }
        List<AudioProcessingStep> steps = stepMapper.selectByPlanId(
                plan.getId());
        return toVO(plan, steps == null ? List.of() : steps);
    }

    private AudioAnalysisReportVO loadReport(Long taskId) {
        try {
            return reportService.getReport(taskId);
        } catch (BusinessException e) {
            log.warn("Required report is unavailable for processing plan, "
                    + "taskId={}, reportErrorCode={}", taskId, e.getCode());
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_DATA_INCOMPLETE,
                    "生成处理方案所需的分析报告不完整");
        }
    }

    private ProcessingPlanVO save(AudioAnalysisTask task,
                                  ProcessingPlanDraft draft) {
        if (draft == null || draft.steps() == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_DATA_INCOMPLETE,
                    "处理方案生成结果不完整");
        }
        LocalDateTime now = LocalDateTime.now();
        AudioProcessingPlan plan = planMapper.selectByTaskIdForUpdate(
                task.getId());
        if (plan == null) {
            plan = new AudioProcessingPlan();
            plan.setId(IdWorker.getId());
            plan.setTaskId(task.getId());
            plan.setCreatedAt(now);
            plan.setPlanRevision(1);
        } else {
            plan.setPlanRevision((plan.getPlanRevision() == null
                    ? 1 : plan.getPlanRevision()) + 1);
        }
        plan.setAudioFileId(task.getAudioFileId());
        plan.setPlanVersion(properties.getProcessingPlan().getVersion());
        plan.setPlanStatus(draft.status().name());
        plan.setSummary(draft.summary());
        plan.setStepCount(draft.steps().size());
        plan.setEstimatedOutputDurationMs(
                draft.estimatedOutputDurationMs());
        plan.setUpdatedAt(now);

        List<AudioProcessingStep> steps = buildSteps(
                plan.getId(), draft.steps(), now);
        ProcessingPlanVO vo = toVO(plan, steps);
        plan.setPlanJson(serialize(vo));
        if (planMapper.upsert(plan) < 1) {
            throw new IllegalStateException("Processing plan was not saved");
        }

        AudioProcessingPlan persisted = planMapper
                .selectByTaskIdForUpdate(task.getId());
        if (persisted == null) {
            throw new IllegalStateException(
                    "Saved processing plan cannot be loaded");
        }
        if (!persisted.getId().equals(plan.getId())) {
            for (AudioProcessingStep step : steps) {
                step.setPlanId(persisted.getId());
            }
            persisted.setAudioFileId(plan.getAudioFileId());
            persisted.setPlanVersion(plan.getPlanVersion());
            persisted.setPlanRevision(plan.getPlanRevision());
            persisted.setPlanStatus(plan.getPlanStatus());
            persisted.setSummary(plan.getSummary());
            persisted.setStepCount(plan.getStepCount());
            persisted.setEstimatedOutputDurationMs(
                    plan.getEstimatedOutputDurationMs());
            persisted.setUpdatedAt(now);
            persisted.setPlanJson(serialize(toVO(persisted, steps)));
            if (planMapper.upsert(persisted) < 1) {
                throw new IllegalStateException(
                        "Concurrent processing plan was not updated");
            }
        } else {
            persisted = plan;
        }

        confirmationMapper.markOldDraftsStale(persisted.getId(),
                persisted.getPlanRevision(), now);
        stepMapper.deleteByPlanId(persisted.getId());
        if (!steps.isEmpty() && stepMapper.insertBatch(steps) != steps.size()) {
            throw new IllegalStateException(
                    "Processing plan steps were not fully saved");
        }
        return toVO(persisted, steps);
    }

    private void validateExecutableOperations(ProcessingPlanDraft draft) {
        if (draft == null || draft.steps() == null) {
            throw new BusinessException(
                    ErrorCode.PROCESSING_PLAN_DATA_INCOMPLETE,
                    "Processing plan generation result is incomplete");
        }
        for (ProcessingStepDraft step : draft.steps()) {
            ProcessingOperationType operation = step == null
                    ? null : step.operationType();
            if (operation == null || !operation.isExecutable()) {
                throw new BusinessException(
                        ErrorCode.PROCESSING_PLAN_GENERATION_FAILED,
                        "Generated plan contains an unsupported operation: "
                                + operation);
            }
        }
    }

    private void validateDraftParameters(ProcessingPlanDraft draft) {
        for (ProcessingStepDraft source : draft.steps()) {
            AudioProcessingStep step = new AudioProcessingStep();
            step.setOperationType(source.operationType().name());
            step.setStartMs(source.startMs());
            step.setEndMs(source.endMs());
            parameterValidator.mergeAndValidate(step, source.parameters(),
                    Map.of());
        }
    }

    private List<AudioProcessingStep> buildSteps(
            Long planId, List<ProcessingStepDraft> drafts,
            LocalDateTime now) {
        List<AudioProcessingStep> steps = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ProcessingStepDraft draft = drafts.get(i);
            AudioProcessingStep step = new AudioProcessingStep();
            step.setId(IdWorker.getId());
            step.setPlanId(planId);
            step.setStepOrder(i + 1);
            step.setOperationType(draft.operationType().name());
            step.setTitle(draft.title());
            step.setDescription(draft.description());
            step.setSourceIssueId(draft.sourceIssueId());
            step.setStartMs(draft.startMs());
            step.setEndMs(draft.endMs());
            step.setPriority(draft.priority().name());
            step.setRiskLevel(draft.riskLevel().name());
            step.setRequiresConfirmation(draft.requiresConfirmation());
            step.setParametersJson(serializeParameters(draft.parameters()));
            step.setReason(draft.reason());
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            steps.add(step);
        }
        return steps;
    }

    private ProcessingPlanVO toVO(AudioProcessingPlan plan,
                                  List<AudioProcessingStep> steps) {
        List<ProcessingPlanVO.Step> records = steps.stream()
                .map(this::toStepVO).toList();
        return ProcessingPlanVO.builder()
                .planId(plan.getId())
                .taskId(plan.getTaskId())
                .audioFileId(plan.getAudioFileId())
                .planVersion(plan.getPlanVersion())
                .planRevision(plan.getPlanRevision())
                .planStatus(plan.getPlanStatus())
                .summary(plan.getSummary())
                .stepCount(records.size())
                .estimatedOutputDurationMs(
                        plan.getEstimatedOutputDurationMs())
                .steps(records)
                .generatedAt(plan.getUpdatedAt() == null
                        ? plan.getCreatedAt() : plan.getUpdatedAt())
                .build();
    }

    private ProcessingPlanVO.Step toStepVO(AudioProcessingStep step) {
        return ProcessingPlanVO.Step.builder()
                .stepId(step.getId())
                .stepOrder(step.getStepOrder())
                .operationType(step.getOperationType())
                .title(step.getTitle())
                .description(step.getDescription())
                .sourceIssueId(step.getSourceIssueId())
                .startMs(step.getStartMs())
                .endMs(step.getEndMs())
                .priority(step.getPriority())
                .riskLevel(step.getRiskLevel())
                .requiresConfirmation(step.getRequiresConfirmation())
                .parameters(parseParameters(step))
                .reason(step.getReason())
                .build();
    }

    private String serialize(ProcessingPlanVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize processing plan", e);
        }
    }

    private String serializeParameters(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters == null
                    ? Map.of() : parameters);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize processing step parameters", e);
        }
    }

    private Map<String, Object> parseParameters(AudioProcessingStep step) {
        if (step.getParametersJson() == null
                || step.getParametersJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(
                    step.getParametersJson(), new TypeReference<>() {
                    });
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            log.warn("Unable to parse processing step parameters, "
                            + "planId={}, stepId={}",
                    step.getPlanId(), step.getId(), e);
            return Map.of();
        }
    }

    private void validateTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "taskId must be greater than 0");
        }
    }
}
