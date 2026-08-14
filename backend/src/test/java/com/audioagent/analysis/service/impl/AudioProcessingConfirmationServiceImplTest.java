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
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingConfirmationServiceImplTest {

    private AudioAnalysisTaskMapper taskMapper;
    private AudioFileMapper fileMapper;
    private AudioProcessingPlanMapper planMapper;
    private AudioProcessingStepMapper sourceStepMapper;
    private AudioProcessingConfirmationMapper confirmationMapper;
    private AudioProcessingStepConfirmationMapper stepConfirmationMapper;
    private ObjectMapper objectMapper;
    private AudioProcessingConfirmationServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        planMapper = mock(AudioProcessingPlanMapper.class);
        sourceStepMapper = mock(AudioProcessingStepMapper.class);
        confirmationMapper = mock(AudioProcessingConfirmationMapper.class);
        stepConfirmationMapper = mock(
                AudioProcessingStepConfirmationMapper.class);
        AnalysisProperties properties = new AnalysisProperties();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AudioProcessingConfirmationServiceImpl(
                taskMapper, fileMapper, planMapper, sourceStepMapper,
                confirmationMapper, stepConfirmationMapper,
                new ProcessingParameterValidator(properties), objectMapper);
    }

    @Test
    void successfulTaskCreatesDraftWithOnePendingDecisionPerStep() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        AudioProcessingPlan plan = plan(2);
        AudioProcessingStep second = source(42L, 2, "INCREASE_GAIN",
                true, "{\"suggestedGainDb\":3.0}");
        AudioProcessingStep first = source(41L, 1, "REVIEW_SILENCE",
                true, "{\"mode\":\"REVIEW_BEFORE_APPLY\"}");
        when(planMapper.selectByTaskIdForUpdate(10L)).thenReturn(plan);
        when(sourceStepMapper.selectByPlanId(30L))
                .thenReturn(List.of(second, first));
        when(confirmationMapper.insertIgnore(any())).thenReturn(1);
        when(stepConfirmationMapper.insertBatch(any())).thenReturn(2);

        ProcessingConfirmationVO result = service.create(7L, 10L);

        assertEquals("DRAFT", result.getConfirmationStatus());
        assertEquals(2, result.getPendingStepCount());
        assertEquals(List.of(1, 2), result.getSteps().stream()
                .map(ProcessingConfirmationVO.Step::getStepOrder).toList());
        assertTrue(result.getSteps().stream()
                .allMatch(step -> "PENDING".equals(step.getDecision())));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioProcessingStepConfirmation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(stepConfirmationMapper).insertBatch(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream()
                .allMatch(step -> "PENDING".equals(step.getDecision())));
    }

    @Test
    void nonSuccessfulTaskCannotCreateDraft() {
        stubOwnedTask(AnalysisTaskStatus.PROCESSING, 7L);

        assertCode(ErrorCode.PROCESSING_PLAN_NOT_READY,
                () -> service.create(7L, 10L));
        verify(confirmationMapper, never()).insertIgnore(any());
    }

    @Test
    void duplicateCreateReturnsExistingRevisionDraft() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        AudioProcessingPlan plan = plan(1);
        AudioProcessingConfirmation confirmation = confirmation("DRAFT");
        AudioProcessingStep source = source(41L, 1, "INCREASE_GAIN",
                false, "{\"suggestedGainDb\":3.0}");
        when(planMapper.selectByTaskIdForUpdate(10L)).thenReturn(plan);
        when(confirmationMapper.selectByPlanRevision(30L, 2))
                .thenReturn(confirmation);
        when(sourceStepMapper.selectByPlanId(30L))
                .thenReturn(List.of(source));
        when(stepConfirmationMapper.selectByConfirmationId(60L))
                .thenReturn(List.of(decision("PENDING", false)));

        ProcessingConfirmationVO result = service.create(7L, 10L);

        assertEquals(60L, result.getConfirmationId());
        verify(confirmationMapper, never()).insertIgnore(any());
        verify(stepConfirmationMapper, never()).insertBatch(any());
    }

    @Test
    void otherUserCannotCreateOrReadConfirmation() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 8L);

        assertCode(ErrorCode.AUDIO_FILE_ACCESS_DENIED,
                () -> service.create(7L, 10L));
        assertCode(ErrorCode.AUDIO_FILE_ACCESS_DENIED,
                () -> service.getCurrent(7L, 10L));
    }

    @Test
    void missingCurrentConfirmationReturnsExplicitError() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        when(planMapper.selectByTaskId(10L)).thenReturn(plan(0));

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_NOT_FOUND,
                () -> service.getCurrent(7L, 10L));
    }

    @Test
    void draftWithNoPlanStepsReturnsEmptyArray() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        when(planMapper.selectByTaskIdForUpdate(10L)).thenReturn(plan(0));
        when(sourceStepMapper.selectByPlanId(30L)).thenReturn(List.of());
        when(confirmationMapper.insertIgnore(any())).thenReturn(1);

        ProcessingConfirmationVO result = service.create(7L, 10L);

        assertNotNull(result.getSteps());
        assertTrue(result.getSteps().isEmpty());
    }

    @Test
    void acceptedStepUpdateMergesOverridesAndRecounts() {
        AudioProcessingStepConfirmation decision = stubEditableStep(
                "PENDING", "INCREASE_GAIN", false,
                "{\"suggestedGainDb\":3.0,\"mode\":\"SUGGESTION_ONLY\"}");
        when(stepConfirmationMapper.selectByConfirmationId(60L))
                .thenReturn(List.of(decision));
        when(stepConfirmationMapper.updateById(
                any(AudioProcessingStepConfirmation.class))).thenReturn(1);
        when(confirmationMapper.updateCounts(anyLong(), anyInt(), anyInt(),
                anyInt(), any())).thenReturn(1);
        UpdateProcessingStepConfirmationRequest request = request(
                "ACCEPTED", false, Map.of("suggestedGainDb", 2.5));

        ProcessingConfirmationVO.Step result = service.updateStep(
                7L, 60L, 70L, request);

        assertEquals("ACCEPTED", result.getDecision());
        assertEquals(2.5, result.getEffectiveParameters()
                .get("suggestedGainDb"));
        assertEquals("SUGGESTION_ONLY",
                result.getEffectiveParameters().get("mode"));
        verify(confirmationMapper).updateCounts(
                60L, 1, 0, 0, decision.getUpdatedAt());
    }

    @Test
    void acceptedPeakLimitWithEmptyOverridesIsSaved() {
        AudioProcessingStepConfirmation decision = stubEditableStep(
                "PENDING", "LIMIT_PEAK", true,
                "{\"truePeakLimitDbfs\":-1.0}");
        when(stepConfirmationMapper.selectByConfirmationId(60L))
                .thenReturn(List.of(decision));
        when(stepConfirmationMapper.updateById(
                any(AudioProcessingStepConfirmation.class))).thenReturn(1);
        when(confirmationMapper.updateCounts(anyLong(), anyInt(), anyInt(),
                anyInt(), any())).thenReturn(1);

        ProcessingConfirmationVO.Step result = service.updateStep(
                7L, 60L, 70L,
                request("ACCEPTED", true, Map.of()));

        assertEquals("ACCEPTED", result.getDecision());
        assertTrue(result.getUserConfirmed());
        assertEquals(-1.0, result.getEffectiveParameters()
                .get("truePeakLimitDbfs"));
        verify(confirmationMapper).updateCounts(
                60L, 1, 0, 0, decision.getUpdatedAt());
    }

    @Test
    void rejectedStepUpdateSucceedsAndRecounts() {
        AudioProcessingStepConfirmation decision = stubEditableStep(
                "PENDING", "REVIEW_SILENCE", true,
                "{\"mode\":\"REVIEW_BEFORE_APPLY\"}");
        when(stepConfirmationMapper.selectByConfirmationId(60L))
                .thenReturn(List.of(decision));
        when(stepConfirmationMapper.updateById(
                any(AudioProcessingStepConfirmation.class))).thenReturn(1);
        when(confirmationMapper.updateCounts(anyLong(), anyInt(), anyInt(),
                anyInt(), any())).thenReturn(1);

        ProcessingConfirmationVO.Step result = service.updateStep(
                7L, 60L, 70L, request("REJECTED", false, Map.of()));

        assertEquals("REJECTED", result.getDecision());
        verify(confirmationMapper).updateCounts(
                60L, 0, 1, 0, decision.getUpdatedAt());
    }

    @Test
    void stepMustBelongToConfirmation() {
        stubEditableConfirmation();
        when(stepConfirmationMapper.selectOwnedStep(60L, 999L))
                .thenReturn(null);

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_NOT_FOUND,
                () -> service.updateStep(7L, 60L, 999L,
                        request("ACCEPTED", false, Map.of())));
    }

    @Test
    void nonWhitelistOverrideIsRejectedWithoutUpdatingStep() {
        stubEditableStep("PENDING", "INCREASE_GAIN", false,
                "{\"suggestedGainDb\":3.0}");

        assertCode(ErrorCode.PROCESSING_PARAMETER_INVALID,
                () -> service.updateStep(7L, 60L, 70L,
                        request("ACCEPTED", false,
                                Map.of("operationType", "LIMIT_PEAK"))));
        verify(stepConfirmationMapper, never()).updateById(
                any(AudioProcessingStepConfirmation.class));
    }

    @Test
    void confirmedDraftCannotBeModified() {
        stubConfirmationForUpdate("CONFIRMED");

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_ALREADY_CONFIRMED,
                () -> service.updateStep(7L, 60L, 70L,
                        request("REJECTED", false, Map.of())));
    }

    @Test
    void pendingStepPreventsFinalConfirmation() {
        stubFinalConfirmation("DRAFT",
                List.of(decision("PENDING", false)),
                List.of(source(41L, 1, "INCREASE_GAIN", false,
                        "{\"suggestedGainDb\":3.0}")));

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_HAS_PENDING_STEPS,
                () -> service.confirm(7L, 60L));
        verify(confirmationMapper, never()).confirmDraft(
                anyLong(), anyString(), any());
    }

    @Test
    void acceptedRiskStepRequiresExplicitUserConfirmation() {
        stubFinalConfirmation("DRAFT",
                List.of(decision("ACCEPTED", false)),
                List.of(source(41L, 1, "TRIM_SEGMENT", true,
                        "{}")));

        assertCode(ErrorCode.PROCESSING_STEP_CONFIRMATION_REQUIRED,
                () -> service.confirm(7L, 60L));
    }

    @Test
    void acceptedLegacyOperationRequiresRegeneratedPlan() {
        stubFinalConfirmation("DRAFT",
                List.of(decision("ACCEPTED", true)),
                List.of(source(41L, 1, "LIMIT_PEAK", true,
                        "{\"truePeakLimitDbfs\":-1.0}")));

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_STALE,
                () -> service.confirm(7L, 60L));
        verify(confirmationMapper, never()).confirmDraft(
                anyLong(), anyString(), any());
    }

    @Test
    void allRejectedCanBeConfirmedAndCreatesImmutableSnapshot()
            throws Exception {
        AudioProcessingStepConfirmation rejected = decision(
                "REJECTED", false);
        AudioProcessingConfirmation confirmation = stubFinalConfirmation(
                "DRAFT", List.of(rejected),
                List.of(source(41L, 1, "DENOISE_REVIEW", true,
                        "{\"suggestedStrength\":\"LIGHT\"}")));
        long longId = 9_223_372_036_854_775_806L;
        confirmation.setId(longId);
        when(confirmationMapper.selectByIdForUpdate(longId))
                .thenReturn(confirmation);
        when(stepConfirmationMapper.selectByConfirmationId(longId))
                .thenReturn(List.of(rejected));
        when(confirmationMapper.confirmDraft(anyLong(), anyString(), any()))
                .thenReturn(1);
        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);

        ProcessingConfirmationVO result = service.confirm(
                7L, confirmation.getId());

        assertEquals("CONFIRMED", result.getConfirmationStatus());
        assertEquals(0, result.getAcceptedStepCount());
        assertNotNull(result.getResultMessage());
        verify(confirmationMapper).confirmDraft(
                anyLong(), snapshot.capture(), any());
        assertTrue(snapshot.getValue().contains(
                "\"9223372036854775806\""));
        assertTrue(snapshot.getValue().contains("\"decision\":\"REJECTED\""));
        assertFalse(snapshot.getValue().contains("parametersJson"));
        assertEquals("CONFIRMED", objectMapper.readTree(snapshot.getValue())
                .get("confirmationStatus").asText());
    }

    @Test
    void changedPlanRevisionMakesDraftStale() {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        when(confirmationMapper.selectByIdForUpdate(60L))
                .thenReturn(confirmation("DRAFT"));
        AudioProcessingPlan newer = plan(1);
        newer.setPlanRevision(3);
        when(planMapper.selectByTaskId(10L)).thenReturn(newer);

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_STALE,
                () -> service.confirm(7L, 60L));
    }

    @Test
    void missingSourceStepMakesDraftStale() {
        stubFinalConfirmation("DRAFT",
                List.of(decision("REJECTED", false)), List.of());

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_STALE,
                () -> service.confirm(7L, 60L));
    }

    @Test
    void duplicateConfirmOnlyAllowsFirstSuccess() {
        stubConfirmationForUpdate("CONFIRMED");

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_ALREADY_CONFIRMED,
                () -> service.confirm(7L, 60L));
        verify(confirmationMapper, never()).confirmDraft(
                anyLong(), anyString(), any());
    }

    @Test
    void draftCanBeCancelledButConfirmedCannot() {
        AudioProcessingConfirmation draft = stubConfirmationForUpdate("DRAFT");
        when(confirmationMapper.cancelDraft(anyLong(), any())).thenReturn(1);
        when(sourceStepMapper.selectByPlanId(30L)).thenReturn(List.of());
        when(stepConfirmationMapper.selectByConfirmationId(60L))
                .thenReturn(List.of());

        ProcessingConfirmationVO cancelled = service.cancel(7L, 60L);
        assertEquals("CANCELLED", cancelled.getConfirmationStatus());

        draft.setConfirmationStatus("CONFIRMED");
        assertCode(ErrorCode.PROCESSING_CONFIRMATION_ALREADY_CONFIRMED,
                () -> service.cancel(7L, 60L));
    }

    @Test
    void cancelledConfirmationCannotBeModified() {
        stubConfirmationForUpdate("CANCELLED");

        assertCode(ErrorCode.PROCESSING_CONFIRMATION_CANCELLED,
                () -> service.updateStep(7L, 60L, 70L,
                        request("REJECTED", false, Map.of())));
    }

    private AudioProcessingStepConfirmation stubEditableStep(
            String status, String operation, boolean requiresConfirmation,
            String parametersJson) {
        stubEditableConfirmation();
        AudioProcessingStep source = source(41L, 1, operation,
                requiresConfirmation, parametersJson);
        AudioProcessingStepConfirmation decision = decision(status, false);
        when(stepConfirmationMapper.selectOwnedStep(60L, 70L))
                .thenReturn(decision);
        when(sourceStepMapper.selectById(41L)).thenReturn(source);
        return decision;
    }

    private AudioProcessingConfirmation stubEditableConfirmation() {
        return stubConfirmationForUpdate("DRAFT");
    }

    private AudioProcessingConfirmation stubConfirmationForUpdate(
            String status) {
        stubOwnedTask(AnalysisTaskStatus.SUCCESS, 7L);
        AudioProcessingConfirmation confirmation = confirmation(status);
        when(confirmationMapper.selectByIdForUpdate(60L))
                .thenReturn(confirmation);
        when(planMapper.selectByTaskId(10L)).thenReturn(plan(1));
        return confirmation;
    }

    private AudioProcessingConfirmation stubFinalConfirmation(
            String status, List<AudioProcessingStepConfirmation> decisions,
            List<AudioProcessingStep> sources) {
        AudioProcessingConfirmation confirmation =
                stubConfirmationForUpdate(status);
        when(sourceStepMapper.selectByPlanId(30L)).thenReturn(sources);
        when(stepConfirmationMapper.selectByConfirmationId(
                confirmation.getId())).thenReturn(decisions);
        return confirmation;
    }

    private void stubOwnedTask(AnalysisTaskStatus status, Long ownerId) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(10L);
        task.setAudioFileId(20L);
        task.setStatus(status);
        AudioFile file = new AudioFile();
        file.setId(20L);
        file.setUserId(ownerId);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(fileMapper.selectById(20L)).thenReturn(file);
    }

    private AudioProcessingPlan plan(int stepCount) {
        AudioProcessingPlan plan = new AudioProcessingPlan();
        plan.setId(30L);
        plan.setTaskId(10L);
        plan.setAudioFileId(20L);
        plan.setPlanVersion(1);
        plan.setPlanRevision(2);
        plan.setPlanStatus("READY");
        plan.setStepCount(stepCount);
        return plan;
    }

    private AudioProcessingConfirmation confirmation(String status) {
        AudioProcessingConfirmation confirmation =
                new AudioProcessingConfirmation();
        confirmation.setId(60L);
        confirmation.setTaskId(10L);
        confirmation.setAudioFileId(20L);
        confirmation.setPlanId(30L);
        confirmation.setSourcePlanRevision(2);
        confirmation.setConfirmationStatus(status);
        confirmation.setAcceptedStepCount(0);
        confirmation.setRejectedStepCount(0);
        confirmation.setPendingStepCount(1);
        confirmation.setCreatedAt(LocalDateTime.now());
        confirmation.setUpdatedAt(LocalDateTime.now());
        return confirmation;
    }

    private AudioProcessingStep source(
            Long id, int order, String operation,
            boolean requiresConfirmation, String parametersJson) {
        AudioProcessingStep source = new AudioProcessingStep();
        source.setId(id);
        source.setPlanId(30L);
        source.setStepOrder(order);
        source.setOperationType(operation);
        source.setTitle(operation);
        source.setStartMs(100L);
        source.setEndMs(1000L);
        source.setRequiresConfirmation(requiresConfirmation);
        source.setParametersJson(parametersJson);
        return source;
    }

    private AudioProcessingStepConfirmation decision(
            String status, boolean userConfirmed) {
        AudioProcessingStepConfirmation decision =
                new AudioProcessingStepConfirmation();
        decision.setId(70L);
        decision.setConfirmationId(60L);
        decision.setSourceStepId(41L);
        decision.setDecision(status);
        decision.setUserConfirmed(userConfirmed);
        decision.setParameterOverridesJson("{}");
        String effective = status.equals("ACCEPTED")
                || status.equals("REJECTED")
                ? "{\"suggestedStrength\":\"LIGHT\"}" :
                "{\"suggestedGainDb\":3.0}";
        decision.setEffectiveParametersJson(effective);
        decision.setCreatedAt(LocalDateTime.now());
        decision.setUpdatedAt(LocalDateTime.now());
        return decision;
    }

    private UpdateProcessingStepConfirmationRequest request(
            String decision, boolean userConfirmed,
            Map<String, Object> overrides) {
        UpdateProcessingStepConfirmationRequest request =
                new UpdateProcessingStepConfirmationRequest();
        request.setDecision(decision);
        request.setUserConfirmed(userConfirmed);
        request.setParameterOverrides(overrides);
        return request;
    }

    private void assertCode(ErrorCode code, Runnable action) {
        BusinessException exception = assertThrows(
                BusinessException.class, action::run);
        assertEquals(code.getCode(), exception.getCode());
    }
}
