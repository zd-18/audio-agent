package com.audioagent.agent.workflow.service.impl;

import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.entity.AgentMessage;
import com.audioagent.agent.mapper.AgentConversationMapper;
import com.audioagent.agent.workflow.entity.AgentProcessingWorkflow;
import com.audioagent.agent.workflow.mapper.AgentProcessingWorkflowMapper;
import com.audioagent.agent.workflow.model.AgentPlannerResult;
import com.audioagent.agent.workflow.model.AgentProcessingContext;
import com.audioagent.agent.workflow.model.AgentWorkflowPlanningResult;
import com.audioagent.agent.workflow.model.AgentWorkflowStatus;
import com.audioagent.agent.workflow.planner.AgentProcessingPlanner;
import com.audioagent.agent.workflow.service.AgentProcessingContextService;
import com.audioagent.agent.workflow.service.AgentProcessingWorkflowService;
import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;
import com.audioagent.analysis.dto.UpdateProcessingStepConfirmationRequest;
import com.audioagent.analysis.service.AudioProcessingConfirmationService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.processing.model.ProcessingExecutionStage;
import com.audioagent.processing.model.ProcessingExecutionStatus;
import com.audioagent.processing.service.AudioProcessingExecutionService;
import com.audioagent.processing.vo.ProcessingExecutionVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentProcessingWorkflowServiceImpl
        implements AgentProcessingWorkflowService {

    private final AgentProcessingWorkflowMapper workflowMapper;
    private final AgentConversationMapper conversationMapper;
    private final AgentProcessingContextService contextService;
    private final AgentProcessingPlanner planner;
    private final AudioProcessingPlanService planService;
    private final AudioProcessingConfirmationService confirmationService;
    private final AudioProcessingExecutionService executionService;

    @Override
    public AgentWorkflowPlanningResult plan(
            Long userId, AgentConversation conversation,
            AgentMessage userMessage, AgentMessage assistantMessage,
            String requirement) {
        AgentProcessingWorkflow existing = workflowMapper
                .selectByUserMessageId(userId, userMessage.getId());
        if (existing != null) {
            return new AgentWorkflowPlanningResult(toVO(synchronize(existing)),
                    plannedMessage(existing), null, null, null, null);
        }

        AgentProcessingContext context = contextService.build(userId,
                conversation, requirement);
        AgentProcessingWorkflow workflow = newWorkflow(userId, conversation,
                userMessage, assistantMessage, context);
        if (workflowMapper.insertIgnore(workflow) == 0) {
            AgentProcessingWorkflow concurrent = workflowMapper
                    .selectByUserMessageId(userId, userMessage.getId());
            if (concurrent == null) {
                throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED);
            }
            return new AgentWorkflowPlanningResult(toVO(synchronize(concurrent)),
                    plannedMessage(concurrent), null, null, null, null);
        }

        try {
            AgentPlannerResult planned = planner.plan(context, requirement);
            ProcessingPlanVO saved = planService.saveAgentPlanForOwner(userId,
                    context.taskId(), planned.plan());
            ProcessingConfirmationVO confirmation = confirmationService.create(
                    userId, context.taskId());
            LocalDateTime now = LocalDateTime.now();
            if (workflowMapper.markWaitingConfirmation(workflow.getId(),
                    saved.getPlanId(), confirmation.getConfirmationId(), now) != 1) {
                throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED,
                        "Workflow state changed while saving the plan");
            }
            workflow.setPlanId(saved.getPlanId());
            workflow.setConfirmationId(confirmation.getConfirmationId());
            workflow.setWorkflowStatus(
                    AgentWorkflowStatus.WAITING_CONFIRMATION.name());
            workflow.setUpdatedAt(now);
            log.info("Agent processing plan ready, workflowId={}, taskId={}, planId={}, stepCount={}",
                    workflow.getId(), workflow.getTaskId(), saved.getPlanId(),
                    saved.getStepCount());
            return new AgentWorkflowPlanningResult(toVO(workflow, saved, null),
                    "已生成处理方案：" + saved.getSummary()
                            + " 请确认后再开始修改音频。",
                    planned.modelName(), planned.promptTokens(),
                    planned.completionTokens(), planned.totalTokens());
        } catch (RuntimeException error) {
            markPlanningFailed(workflow, error);
            throw error;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentProcessingWorkflowVO confirm(Long userId, Long workflowId) {
        AgentProcessingWorkflow workflow = workflowMapper
                .selectByIdForUpdate(workflowId);
        ensureOwned(userId, workflow);
        AgentWorkflowStatus status = status(workflow);
        if (status == AgentWorkflowStatus.EXECUTING
                || status == AgentWorkflowStatus.REVIEWING
                || status == AgentWorkflowStatus.SUCCESS) {
            return toVO(synchronize(workflow));
        }
        if (status != AgentWorkflowStatus.WAITING_CONFIRMATION) {
            throw new BusinessException(ErrorCode.AGENT_WORKFLOW_STATUS_INVALID,
                    "Only a waiting plan can be confirmed");
        }

        ProcessingConfirmationVO confirmation = confirmationService
                .getCurrent(userId, workflow.getTaskId());
        if (!workflow.getConfirmationId().equals(
                confirmation.getConfirmationId())) {
            throw new BusinessException(ErrorCode.AGENT_WORKFLOW_STATUS_INVALID,
                    "The processing plan is no longer current");
        }
        for (ProcessingConfirmationVO.Step step : confirmation.getSteps()) {
            UpdateProcessingStepConfirmationRequest request =
                    new UpdateProcessingStepConfirmationRequest();
            request.setDecision("ACCEPTED");
            request.setUserConfirmed(true);
            request.setParameterOverrides(Map.of());
            confirmationService.updateStep(userId,
                    confirmation.getConfirmationId(),
                    step.getStepConfirmationId(), request);
        }
        confirmationService.confirm(userId, confirmation.getConfirmationId());
        ProcessingExecutionVO execution = executionService.create(userId,
                confirmation.getConfirmationId());
        LocalDateTime now = LocalDateTime.now();
        if (workflowMapper.markExecuting(workflowId,
                execution.getExecutionId(), now) != 1) {
            throw new BusinessException(ErrorCode.AGENT_WORKFLOW_STATUS_INVALID,
                    "Workflow state changed before execution started");
        }
        workflow.setExecutionId(execution.getExecutionId());
        workflow.setWorkflowStatus(AgentWorkflowStatus.EXECUTING.name());
        workflow.setUpdatedAt(now);
        log.info("Agent processing execution started, workflowId={}, executionId={}",
                workflowId, execution.getExecutionId());
        return toVO(workflow, planService.get(userId, workflow.getTaskId()),
                execution);
    }

    @Override
    public AgentProcessingWorkflowVO get(Long userId, Long workflowId) {
        AgentProcessingWorkflow workflow = workflowMapper.selectById(workflowId);
        ensureOwned(userId, workflow);
        return toVO(synchronize(workflow));
    }

    @Override
    public AgentProcessingWorkflowVO findByUserMessage(
            Long userId, Long userMessageId) {
        AgentProcessingWorkflow workflow = workflowMapper
                .selectByUserMessageId(userId, userMessageId);
        return workflow == null ? null : toVO(synchronize(workflow));
    }

    @Override
    public List<AgentProcessingWorkflowVO> listByConversation(
            Long userId, Long conversationId) {
        AgentConversation conversation = conversationMapper.selectById(
                conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_NOT_FOUND,
                    "资源不存在或不可访问");
        }
        return workflowMapper.selectByConversation(userId, conversationId)
                .stream().map(this::synchronize).map(this::toVO).toList();
    }

    private AgentProcessingWorkflow synchronize(
            AgentProcessingWorkflow workflow) {
        AgentWorkflowStatus current = status(workflow);
        if (workflow.getExecutionId() == null
                || !(current == AgentWorkflowStatus.EXECUTING
                || current == AgentWorkflowStatus.REVIEWING)) {
            return workflow;
        }
        ProcessingExecutionVO execution = executionService.get(
                workflow.getUserId(), workflow.getExecutionId());
        AgentWorkflowStatus next = mapStatus(execution);
        if (next != current
                || !equals(workflow.getResultFileId(),
                execution.getResultFileId())
                || !equals(workflow.getFailureReason(),
                execution.getFailureMessage())) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime finishedAt = next == AgentWorkflowStatus.SUCCESS
                    || next == AgentWorkflowStatus.FAILED ? now : null;
            workflowMapper.updateOutcome(workflow.getId(), next.name(),
                    execution.getResultFileId(), execution.getFailureMessage(),
                    now, finishedAt);
            workflow.setWorkflowStatus(next.name());
            workflow.setResultFileId(execution.getResultFileId());
            workflow.setFailureReason(execution.getFailureMessage());
            workflow.setUpdatedAt(now);
            workflow.setFinishedAt(finishedAt);
        }
        return workflow;
    }

    private AgentWorkflowStatus mapStatus(ProcessingExecutionVO execution) {
        ProcessingExecutionStatus status = ProcessingExecutionStatus.valueOf(
                execution.getExecutionStatus());
        if (status == ProcessingExecutionStatus.SUCCESS) {
            return AgentWorkflowStatus.SUCCESS;
        }
        if (status == ProcessingExecutionStatus.FAILED
                || status == ProcessingExecutionStatus.DEAD_LETTER
                || status == ProcessingExecutionStatus.CANCELLED) {
            return AgentWorkflowStatus.FAILED;
        }
        if (ProcessingExecutionStage.REVIEWING.name().equals(
                execution.getCurrentStage())) {
            return AgentWorkflowStatus.REVIEWING;
        }
        return AgentWorkflowStatus.EXECUTING;
    }

    private AgentProcessingWorkflowVO toVO(
            AgentProcessingWorkflow workflow) {
        ProcessingPlanVO plan = workflow.getPlanId() == null
                ? null : planService.get(workflow.getUserId(),
                workflow.getTaskId());
        ProcessingExecutionVO execution = workflow.getExecutionId() == null
                ? null : executionService.get(workflow.getUserId(),
                workflow.getExecutionId());
        return toVO(workflow, plan, execution);
    }

    private AgentProcessingWorkflowVO toVO(
            AgentProcessingWorkflow workflow, ProcessingPlanVO plan,
            ProcessingExecutionVO execution) {
        List<AgentProcessingWorkflowVO.Step> steps = plan == null
                ? List.of() : plan.getSteps().stream().map(step ->
                AgentProcessingWorkflowVO.Step.builder()
                        .order(step.getStepOrder())
                        .operationType(step.getOperationType())
                        .title(step.getTitle())
                        .reason(step.getReason())
                        .startMs(step.getStartMs())
                        .endMs(step.getEndMs())
                        .build()).toList();
        return AgentProcessingWorkflowVO.builder()
                .workflowId(workflow.getId())
                .conversationId(workflow.getConversationId())
                .userMessageId(workflow.getUserMessageId())
                .assistantMessageId(workflow.getAssistantMessageId())
                .taskId(workflow.getTaskId())
                .audioFileId(workflow.getAudioFileId())
                .planId(workflow.getPlanId())
                .confirmationId(workflow.getConfirmationId())
                .executionId(workflow.getExecutionId())
                .resultFileId(workflow.getResultFileId())
                .status(workflow.getWorkflowStatus())
                .summary(plan == null ? null : plan.getSummary())
                .steps(steps)
                .progressPercent(execution == null ? null
                        : execution.getProgressPercent())
                .failureReason(workflow.getFailureReason())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .finishedAt(workflow.getFinishedAt())
                .build();
    }

    private AgentProcessingWorkflow newWorkflow(
            Long userId, AgentConversation conversation,
            AgentMessage userMessage, AgentMessage assistantMessage,
            AgentProcessingContext context) {
        LocalDateTime now = LocalDateTime.now();
        AgentProcessingWorkflow workflow = new AgentProcessingWorkflow();
        workflow.setId(IdWorker.getId());
        workflow.setUserId(userId);
        workflow.setConversationId(conversation.getId());
        workflow.setUserMessageId(userMessage.getId());
        workflow.setAssistantMessageId(assistantMessage.getId());
        workflow.setTaskId(context.taskId());
        workflow.setAudioFileId(context.audioFileId());
        workflow.setWorkflowStatus(AgentWorkflowStatus.PLANNING.name());
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        return workflow;
    }

    private void markPlanningFailed(AgentProcessingWorkflow workflow,
                                    RuntimeException error) {
        LocalDateTime now = LocalDateTime.now();
        workflowMapper.updateOutcome(workflow.getId(),
                AgentWorkflowStatus.FAILED.name(), null,
                planningFailureReason(error), now, now);
    }

    private String planningFailureReason(RuntimeException error) {
        if (error instanceof BusinessException business
                && business.getMessage() != null) {
            return business.getMessage();
        }
        return "生成的处理方案未通过安全校验，请调整需求后重试。";
    }

    private String plannedMessage(AgentProcessingWorkflow workflow) {
        return status(workflow) == AgentWorkflowStatus.WAITING_CONFIRMATION
                ? "处理方案已生成，请确认后再开始修改音频。"
                : "已恢复之前的音频处理请求。";
    }

    private void ensureOwned(Long userId,
                             AgentProcessingWorkflow workflow) {
        if (workflow == null || !userId.equals(workflow.getUserId())) {
            throw new BusinessException(ErrorCode.AGENT_WORKFLOW_NOT_FOUND,
                    "资源不存在或不可访问");
        }
    }

    private AgentWorkflowStatus status(AgentProcessingWorkflow workflow) {
        try {
            return AgentWorkflowStatus.valueOf(workflow.getWorkflowStatus());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AGENT_WORKFLOW_STATUS_INVALID);
        }
    }

    private boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
