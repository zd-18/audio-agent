package com.audioagent.processing.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.dispatch.AudioProcessingExecutionDispatcher;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.executor.ProcessingExecutionWorkDirectory;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.snapshot.ProcessingExecutionSnapshotParser;
import com.audioagent.processing.vo.ProcessingExecutionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AudioProcessingExecutionServiceImplTest {

    private AudioProcessingProperties properties;
    private AudioProcessingConfirmationMapper confirmationMapper;
    private AudioAnalysisTaskMapper taskMapper;
    private AudioFileMapper fileMapper;
    private AudioProcessingExecutionMapper executionMapper;
    private AudioProcessingExecutionStepMapper stepMapper;
    private AudioProcessingExecutionDispatcher dispatcher;
    private ProcessingExecutionWorkDirectory workDirectories;
    private ObjectMapper objectMapper;
    private AudioProcessingExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AudioProcessingProperties();
        confirmationMapper = mock(AudioProcessingConfirmationMapper.class);
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        executionMapper = mock(AudioProcessingExecutionMapper.class);
        stepMapper = mock(AudioProcessingExecutionStepMapper.class);
        dispatcher = mock(AudioProcessingExecutionDispatcher.class);
        workDirectories = mock(ProcessingExecutionWorkDirectory.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AudioProcessingExecutionServiceImpl(properties,
                confirmationMapper, taskMapper, fileMapper,
                executionMapper, stepMapper,
                new ProcessingExecutionSnapshotParser(objectMapper),
                dispatcher, workDirectories, objectMapper);
    }

    @Test
    void confirmedSnapshotCreatesExecutionAndDispatches() throws Exception {
        stubReadyConfirmation(List.of(step(1, "NORMALIZE_VOLUME",
                "ACCEPTED", normalizationParameters())), 1);
        when(executionMapper.insertIgnore(any())).thenReturn(1);
        when(stepMapper.insertBatch(any())).thenReturn(1);

        ProcessingExecutionVO result = service.create(7L, 60L);

        assertEquals("PENDING", result.getExecutionStatus());
        assertEquals(1, result.getExecutableStepCount());
        assertEquals(1, result.getSteps().size());
        verify(dispatcher).dispatch(result.getExecutionId());
    }

    @Test
    void draftConfirmationCannotCreateExecution() {
        stubConfirmationStatus("DRAFT", 1);
        assertCode(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                () -> service.create(7L, 60L));
    }

    @Test
    void staleConfirmationCannotCreateExecution() {
        stubConfirmationStatus("STALE", 1);
        assertCode(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                () -> service.create(7L, 60L));
    }

    @Test
    void cancelledConfirmationCannotCreateExecution() {
        stubConfirmationStatus("CANCELLED", 1);
        assertCode(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                () -> service.create(7L, 60L));
    }

    @Test
    void zeroAcceptedStepsCannotCreateExecution() {
        stubConfirmationStatus("CONFIRMED", 0);
        assertCode(ErrorCode.PROCESSING_EXECUTION_NO_ACCEPTED_STEPS,
                () -> service.create(7L, 60L));
    }

    @Test
    void duplicateCreateReturnsExistingWithoutDispatch() {
        stubConfirmationStatus("CONFIRMED", 1);
        AudioProcessingExecution existing = execution("QUEUED", 7L);
        when(executionMapper.selectByConfirmationId(60L))
                .thenReturn(existing);
        when(stepMapper.selectByExecutionId(existing.getId()))
                .thenReturn(List.of());

        ProcessingExecutionVO result = service.create(7L, 60L);

        assertEquals(existing.getId(), result.getExecutionId());
        verify(dispatcher, never()).dispatch(any());
        verify(executionMapper, never()).insertIgnore(any());
    }

    @Test
    void rejectedAndPendingStepsAreNotCopied() throws Exception {
        stubReadyConfirmation(List.of(
                step(1, "TRIM_SEGMENT", "ACCEPTED", Map.of()),
                step(2, "DECREASE_GAIN", "REJECTED",
                        Map.of("suggestedGainDb", -3)),
                step(3, "REVIEW_SILENCE", "PENDING", Map.of())), 1);
        when(executionMapper.insertIgnore(any())).thenReturn(1);
        when(stepMapper.insertBatch(any())).thenReturn(1);
        ArgumentCaptor<List<AudioProcessingExecutionStep>> steps =
                listCaptor();

        service.create(7L, 60L);

        verify(stepMapper).insertBatch(steps.capture());
        assertEquals(1, steps.getValue().size());
        assertEquals("TRIM_SEGMENT",
                steps.getValue().getFirst().getOperationType());
    }

    @Test
    void acceptedLegacyOperationIsRejected() throws Exception {
        stubReadyConfirmation(List.of(step(1, "REVIEW_SILENCE",
                "ACCEPTED", Map.of())), 1);
        assertCode(ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION,
                () -> service.create(7L, 60L));
        verify(executionMapper, never()).insertIgnore(any());
    }

    @Test
    void snapshotCountMismatchIsRejected() throws Exception {
        stubReadyConfirmation(List.of(step(1, "NORMALIZE_VOLUME",
                "REJECTED", normalizationParameters())), 1);
        assertCode(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                () -> service.create(7L, 60L));
    }

    @Test
    void otherUserCannotCreateExecution() throws Exception {
        stubReadyConfirmation(List.of(step(1, "NORMALIZE_VOLUME",
                "ACCEPTED", normalizationParameters())), 1);
        AudioFile source = fileMapper.selectById(20L);
        source.setUserId(8L);

        assertCode(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                () -> service.create(7L, 60L));
    }

    @Test
    void foreignConfirmationNeverLeaksStatusOrAcceptedCount() {
        stubConfirmationStatus("DRAFT", 0);
        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectById(60L);
        fileMapper.selectById(20L).setUserId(8L);

        String expectedMessage = null;
        for (String status : List.of("DRAFT", "STALE", "CANCELLED",
                "CONFIRMED")) {
            confirmation.setConfirmationStatus(status);
            confirmation.setAcceptedStepCount(
                    "CONFIRMED".equals(status) ? 1 : 0);
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.create(7L, 60L));
            assertEquals(ErrorCode
                    .PROCESSING_EXECUTION_CONFIRMATION_NOT_READY.getCode(),
                    error.getCode());
            if (expectedMessage == null) {
                expectedMessage = error.getMessage();
            } else {
                assertEquals(expectedMessage, error.getMessage());
            }
        }

        verify(executionMapper, never()).selectByConfirmationId(any());
        verify(executionMapper, never()).insertIgnore(any());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void queryAlwaysReturnsEmptyStepArrayInsteadOfNull() {
        AudioProcessingExecution execution = execution("SUCCESS", 7L);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);
        when(stepMapper.selectByExecutionId(execution.getId()))
                .thenReturn(null);

        ProcessingExecutionVO result = service.get(7L, execution.getId());

        assertNotNull(result.getSteps());
        assertTrue(result.getSteps().isEmpty());
    }

    @Test
    void queryHidesPersistedDurationDiagnosticsFromUsers() {
        String internalMessage = "Processed audio duration is outside "
                + "tolerance: expectedDurationMs=79931, "
                + "actualDurationMs=76558, differenceMs=3373, "
                + "toleranceMs=1000";
        AudioProcessingExecution execution = execution("FAILED", 7L);
        execution.setFailureCode(
                ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID.name());
        execution.setFailureMessage(internalMessage);
        AudioProcessingExecutionStep step =
                new AudioProcessingExecutionStep();
        step.setFailureMessage(internalMessage);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);
        when(stepMapper.selectByExecutionId(execution.getId()))
                .thenReturn(List.of(step));

        ProcessingExecutionVO result = service.get(7L, execution.getId());

        assertEquals("处理后的音频时长异常，请重新处理或检查源文件。",
                result.getFailureMessage());
        assertEquals("处理后的音频时长异常，请重新处理或检查源文件。",
                result.getSteps().getFirst().getFailureMessage());
    }

    @Test
    void otherUserCannotQueryExecution() {
        AudioProcessingExecution execution = execution("SUCCESS", 8L);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);
        assertCode(ErrorCode.PROCESSING_EXECUTION_NOT_FOUND,
                () -> service.get(7L, execution.getId()));
    }

    @Test
    void foreignExecutionCannotBeRetried() {
        AudioProcessingExecution execution = execution("FAILED", 8L);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);

        assertCode(ErrorCode.PROCESSING_EXECUTION_NOT_FOUND,
                () -> service.retry(7L, execution.getId()));

        verify(executionMapper, never()).resetForManualRetry(any(), any());
        verifyNoInteractions(dispatcher, workDirectories);
    }

    @Test
    void foreignAndMissingTasksUseSameExecutionLookupSemantics() {
        AudioAnalysisTask foreignTask = new AudioAnalysisTask();
        foreignTask.setId(10L);
        foreignTask.setAudioFileId(20L);
        AudioFile foreignFile = new AudioFile();
        foreignFile.setId(20L);
        foreignFile.setUserId(8L);
        when(taskMapper.selectById(10L)).thenReturn(foreignTask);
        when(fileMapper.selectById(20L)).thenReturn(foreignFile);

        BusinessException foreign = assertThrows(BusinessException.class,
                () -> service.getByTask(7L, 10L));
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.getByTask(7L, 11L));

        assertEquals(ErrorCode.AUDIO_TASK_NOT_FOUND.getCode(),
                foreign.getCode());
        assertEquals(foreign.getCode(), missing.getCode());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verify(confirmationMapper, never())
                .selectLatestConfirmedByTaskId(any());
    }

    @Test
    void failedExecutionRetriesWithoutCreatingNewExecution() {
        AudioProcessingExecution execution = execution("FAILED", 7L);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);
        when(executionMapper.resetForManualRetry(eq(execution.getId()),
                any())).thenReturn(1);
        when(stepMapper.selectByExecutionId(execution.getId()))
                .thenReturn(List.of());

        ProcessingExecutionVO result = service.retry(7L,
                execution.getId());

        assertEquals(execution.getId(), result.getExecutionId());
        verify(workDirectories).clean(execution.getId());
        verify(stepMapper).resetForRetry(eq(execution.getId()), any());
        verify(dispatcher).dispatch(execution.getId());
        verify(executionMapper, never()).insertIgnore(any());
    }

    @Test
    void successExecutionCannotRetry() {
        AudioProcessingExecution execution = execution("SUCCESS", 7L);
        execution.setResultFileId(999L);
        when(executionMapper.selectExecutionById(execution.getId()))
                .thenReturn(execution);
        assertCode(ErrorCode.PROCESSING_EXECUTION_NOT_RETRYABLE,
                () -> service.retry(7L, execution.getId()));
    }

    @Test
    void disabledConfigurationRejectsNewExecution() {
        properties.setEnabled(false);
        assertCode(ErrorCode.PROCESSING_EXECUTION_FAILED,
                () -> service.create(7L, 60L));
    }

    @Test
    void longIdsSerializeAsStrings() throws Exception {
        ProcessingExecutionVO vo = ProcessingExecutionVO.builder()
                .executionId(9_223_372_036_854_775_806L)
                .taskId(9_223_372_036_854_775_805L)
                .steps(List.of())
                .build();
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("\"9223372036854775806\""));
        assertTrue(json.contains("\"9223372036854775805\""));
    }

    private void stubReadyConfirmation(
            List<ProcessingConfirmationVO.Step> steps,
            int acceptedCount) throws Exception {
        stubConfirmationStatus("CONFIRMED", acceptedCount);
        AudioProcessingConfirmation confirmation = confirmationMapper
                .selectById(60L);
        ProcessingConfirmationVO snapshot = ProcessingConfirmationVO.builder()
                .confirmationId(60L).taskId(10L).audioFileId(20L)
                .planId(30L).sourcePlanRevision(2)
                .confirmationStatus("CONFIRMED")
                .acceptedStepCount(acceptedCount)
                .rejectedStepCount(0).pendingStepCount(0)
                .steps(steps).build();
        confirmation.setConfirmationJson(
                objectMapper.writeValueAsString(snapshot));
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(10L);
        task.setAudioFileId(20L);
        task.setStatus(AnalysisTaskStatus.SUCCESS);
        when(taskMapper.selectById(10L)).thenReturn(task);
        AudioFile file = new AudioFile();
        file.setId(20L);
        file.setUserId(7L);
        when(fileMapper.selectById(20L)).thenReturn(file);
    }

    private void stubConfirmationStatus(String status, int accepted) {
        AudioProcessingConfirmation confirmation =
                new AudioProcessingConfirmation();
        confirmation.setId(60L);
        confirmation.setTaskId(10L);
        confirmation.setAudioFileId(20L);
        confirmation.setPlanId(30L);
        confirmation.setSourcePlanRevision(2);
        confirmation.setConfirmationStatus(status);
        confirmation.setAcceptedStepCount(accepted);
        when(confirmationMapper.selectById(60L)).thenReturn(confirmation);
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(10L);
        task.setAudioFileId(20L);
        task.setStatus(AnalysisTaskStatus.SUCCESS);
        when(taskMapper.selectById(10L)).thenReturn(task);
        AudioFile file = new AudioFile();
        file.setId(20L);
        file.setUserId(7L);
        when(fileMapper.selectById(20L)).thenReturn(file);
    }

    private ProcessingConfirmationVO.Step step(
            int order, String operation, String decision,
            Map<String, Object> parameters) {
        Long start = operation.equals("NORMALIZE_VOLUME") ? null : 100L;
        Long end = start == null ? null : 1_000L;
        return ProcessingConfirmationVO.Step.builder()
                .stepConfirmationId(100L + order)
                .sourceStepId(200L + order)
                .stepOrder(order)
                .operationType(operation)
                .decision(decision)
                .userConfirmed(true)
                .startMs(start).endMs(end)
                .effectiveParameters(parameters).build();
    }

    private Map<String, Object> normalizationParameters() {
        return Map.of("targetLufs", -16,
                "truePeakLimitDbfs", -1);
    }

    private AudioProcessingExecution execution(String status, Long userId) {
        AudioProcessingExecution execution =
                new AudioProcessingExecution();
        execution.setId(90L);
        execution.setUserId(userId);
        execution.setTaskId(10L);
        execution.setAudioFileId(20L);
        execution.setConfirmationId(60L);
        execution.setExecutionStatus(status);
        execution.setProgressPercent(0);
        execution.setAcceptedStepCount(1);
        execution.setExecutableStepCount(1);
        execution.setSkippedStepCount(0);
        execution.setRetryCount(0);
        return execution;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<AudioProcessingExecutionStep>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private void assertCode(ErrorCode code, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class,
                action::run);
        assertEquals(code.getCode(), error.getCode());
    }
}
