package com.audioagent.agent.workflow.service;

import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.entity.AgentMessage;
import com.audioagent.agent.mapper.AgentConversationMapper;
import com.audioagent.agent.workflow.entity.AgentProcessingWorkflow;
import com.audioagent.agent.workflow.mapper.AgentProcessingWorkflowMapper;
import com.audioagent.agent.workflow.model.AgentPlannerResult;
import com.audioagent.agent.workflow.model.AgentProcessingContext;
import com.audioagent.agent.workflow.model.AgentWorkflowStatus;
import com.audioagent.agent.workflow.planner.AgentProcessingPlanner;
import com.audioagent.agent.workflow.service.impl.AgentProcessingWorkflowServiceImpl;
import com.audioagent.analysis.dto.UpdateProcessingStepConfirmationRequest;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingPlanDraft;
import com.audioagent.analysis.processing.ProcessingPlanStatus;
import com.audioagent.analysis.processing.ProcessingPriority;
import com.audioagent.analysis.processing.ProcessingRiskLevel;
import com.audioagent.analysis.processing.ProcessingStepDraft;
import com.audioagent.analysis.service.AudioProcessingConfirmationService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.processing.service.AudioProcessingExecutionService;
import com.audioagent.processing.vo.ProcessingExecutionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentProcessingWorkflowServiceImplTest {

    private AgentProcessingWorkflowMapper workflowMapper;
    private AgentProcessingContextService contextService;
    private AgentProcessingPlanner planner;
    private AudioProcessingPlanService planService;
    private AudioProcessingConfirmationService confirmationService;
    private AudioProcessingExecutionService executionService;
    private AgentProcessingWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        workflowMapper = mock(AgentProcessingWorkflowMapper.class);
        contextService = mock(AgentProcessingContextService.class);
        planner = mock(AgentProcessingPlanner.class);
        planService = mock(AudioProcessingPlanService.class);
        confirmationService = mock(AudioProcessingConfirmationService.class);
        executionService = mock(AudioProcessingExecutionService.class);
        service = new AgentProcessingWorkflowServiceImpl(workflowMapper,
                mock(AgentConversationMapper.class), contextService, planner,
                planService, confirmationService, executionService);
    }

    @Test
    void plansAndWaitsWithoutExecutingUntilUserConfirms() {
        seedPlanning();

        var result = service.plan(7L, conversation(), message(101L),
                message(102L), "裁掉 1 到 2 秒并统一音量");

        assertEquals("WAITING_CONFIRMATION",
                result.workflow().getStatus());
        verify(executionService, never()).create(any(), any());
        verify(planService).saveAgentPlanForOwner(eq(7L), eq(31L), any());
    }

    @Test
    void confirmationStartsExactlyOneExecutionAndDuplicateIsIdempotent() {
        AgentProcessingWorkflow workflow = waitingWorkflow();
        when(workflowMapper.selectByIdForUpdate(700L)).thenReturn(workflow);
        when(confirmationService.getCurrent(7L, 31L))
                .thenReturn(confirmation());
        when(confirmationService.confirm(7L, 501L))
                .thenReturn(confirmation());
        when(executionService.create(7L, 501L)).thenReturn(execution());
        when(executionService.get(7L, 601L)).thenReturn(execution());
        when(workflowMapper.markExecuting(eq(700L), eq(601L), any()))
                .thenReturn(1);
        when(planService.get(31L)).thenReturn(plan());

        var first = service.confirm(7L, 700L);
        var duplicate = service.confirm(7L, 700L);

        assertEquals("EXECUTING", first.getStatus());
        assertEquals("EXECUTING", duplicate.getStatus());
        verify(executionService, times(1)).create(7L, 501L);
        ArgumentCaptor<UpdateProcessingStepConfirmationRequest> request =
                ArgumentCaptor.forClass(
                        UpdateProcessingStepConfirmationRequest.class);
        verify(confirmationService).updateStep(eq(7L), eq(501L),
                eq(502L), request.capture());
        assertEquals("ACCEPTED", request.getValue().getDecision());
        assertEquals(true, request.getValue().getUserConfirmed());
    }

    @Test
    void planningFailureBecomesFailedWithoutAutomaticRetry() {
        when(contextService.build(any(), any(), any())).thenReturn(context());
        when(workflowMapper.insertIgnore(any())).thenReturn(1);
        when(planner.plan(any(), any())).thenThrow(
                new IllegalArgumentException("invalid plan"));

        assertThrows(IllegalArgumentException.class,
                () -> service.plan(7L, conversation(), message(101L),
                        message(102L), "非法请求"));
        verify(planner, times(1)).plan(any(), any());
        verify(workflowMapper).updateOutcome(any(),
                eq(AgentWorkflowStatus.FAILED.name()), eq(null), any(),
                any(), any());
        verify(executionService, never()).create(any(), any());
    }

    private void seedPlanning() {
        when(contextService.build(any(), any(), any())).thenReturn(context());
        when(workflowMapper.insertIgnore(any())).thenReturn(1);
        when(planner.plan(any(), any())).thenReturn(new AgentPlannerResult(
                draft(), "model", 1, 2, 3));
        when(planService.saveAgentPlanForOwner(7L, 31L, draft()))
                .thenReturn(plan());
        when(confirmationService.create(7L, 31L))
                .thenReturn(confirmation());
        when(workflowMapper.markWaitingConfirmation(any(), eq(401L),
                eq(501L), any())).thenReturn(1);
    }

    private AgentConversation conversation() {
        AgentConversation value = new AgentConversation();
        value.setId(10L);
        value.setUserId(7L);
        value.setTranscriptId(20L);
        return value;
    }

    private AgentMessage message(long id) {
        AgentMessage value = new AgentMessage();
        value.setId(id);
        return value;
    }

    private AgentProcessingContext context() {
        return new AgentProcessingContext(31L, 21L, "audio.mp3",
                10_000L, 48_000, 2, "segments");
    }

    private ProcessingPlanDraft draft() {
        return new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                "统一音量", 10_000L, List.of(new ProcessingStepDraft(
                ProcessingOperationType.NORMALIZE_VOLUME, "统一音量",
                "统一整段音量", null, null, null,
                ProcessingPriority.MEDIUM, ProcessingRiskLevel.MEDIUM,
                true, Map.of("targetLufs", -16,
                "truePeakLimitDbfs", -1), "改善听感")), 0, 0);
    }

    private ProcessingPlanVO plan() {
        return ProcessingPlanVO.builder().planId(401L).taskId(31L)
                .audioFileId(21L).planStatus("READY")
                .summary("统一音量").stepCount(1).steps(List.of(
                        ProcessingPlanVO.Step.builder().stepOrder(1)
                                .operationType("NORMALIZE_VOLUME")
                                .title("统一音量").reason("改善听感")
                                .build())).build();
    }

    private ProcessingConfirmationVO confirmation() {
        return ProcessingConfirmationVO.builder().confirmationId(501L)
                .taskId(31L).planId(401L).confirmationStatus("DRAFT")
                .steps(List.of(ProcessingConfirmationVO.Step.builder()
                        .stepConfirmationId(502L).sourceStepId(402L)
                        .build())).build();
    }

    private AgentProcessingWorkflow waitingWorkflow() {
        AgentProcessingWorkflow workflow = new AgentProcessingWorkflow();
        workflow.setId(700L);
        workflow.setUserId(7L);
        workflow.setConversationId(10L);
        workflow.setUserMessageId(101L);
        workflow.setAssistantMessageId(102L);
        workflow.setTaskId(31L);
        workflow.setAudioFileId(21L);
        workflow.setPlanId(401L);
        workflow.setConfirmationId(501L);
        workflow.setWorkflowStatus("WAITING_CONFIRMATION");
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        return workflow;
    }

    private ProcessingExecutionVO execution() {
        return ProcessingExecutionVO.builder().executionId(601L)
                .executionStatus("PENDING").progressPercent(0).build();
    }
}
