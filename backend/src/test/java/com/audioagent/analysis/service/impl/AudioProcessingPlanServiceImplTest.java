package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.mapper.AudioProcessingPlanMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.mapper.AudioProcessingStepMapper;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingPlanDraft;
import com.audioagent.analysis.processing.ProcessingPlanGenerator;
import com.audioagent.analysis.processing.ProcessingPlanStatus;
import com.audioagent.analysis.processing.ProcessingPriority;
import com.audioagent.analysis.processing.ProcessingRiskLevel;
import com.audioagent.analysis.processing.ProcessingStepDraft;
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.setting.service.UserSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingPlanServiceImplTest {

    private AudioAnalysisTaskMapper taskMapper;
    private AudioAnalysisResultMapper resultMapper;
    private AudioIssueSegmentMapper issueMapper;
    private AudioProcessingPlanMapper planMapper;
    private AudioProcessingStepMapper stepMapper;
    private AudioProcessingConfirmationMapper confirmationMapper;
    private AudioAnalysisReportService reportService;
    private ProcessingPlanGenerator generator;
    private UserSettingService userSettingService;
    private AnalysisProperties properties;
    private ObjectMapper objectMapper;
    private AudioProcessingPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        resultMapper = mock(AudioAnalysisResultMapper.class);
        issueMapper = mock(AudioIssueSegmentMapper.class);
        planMapper = mock(AudioProcessingPlanMapper.class);
        stepMapper = mock(AudioProcessingStepMapper.class);
        confirmationMapper = mock(AudioProcessingConfirmationMapper.class);
        reportService = mock(AudioAnalysisReportService.class);
        generator = mock(ProcessingPlanGenerator.class);
        userSettingService = mock(UserSettingService.class);
        properties = new AnalysisProperties();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AudioProcessingPlanServiceImpl(taskMapper,
                resultMapper, issueMapper, planMapper, stepMapper,
                confirmationMapper, mock(AudioFileMapper.class),
                reportService, generator,
                mock(ProcessingParameterValidator.class),
                userSettingService, properties, objectMapper);
    }

    @Test
    void processingAndFailedTasksCannotGenerateReadyPlan() {
        for (AnalysisTaskStatus status : List.of(
                AnalysisTaskStatus.PROCESSING, AnalysisTaskStatus.FAILED)) {
            when(taskMapper.selectById(10L)).thenReturn(task(status));
            BusinessException exception = assertThrows(
                    BusinessException.class, () -> service.generate(10L));
            assertEquals(ErrorCode.PROCESSING_PLAN_NOT_READY.getCode(),
                    exception.getCode());
        }
        verify(generator, never()).generate(any());
    }

    @Test
    void disabledPlanDoesNotReadOrWriteAnalysisData() {
        properties.getProcessingPlan().setEnabled(false);
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.generate(10L));
        assertEquals(ErrorCode.PROCESSING_PLAN_NOT_READY.getCode(),
                exception.getCode());
        verify(taskMapper, never()).selectById(any());
        verify(planMapper, never()).upsert(any());
    }

    @Test
    void repeatedGenerationKeepsPlanIdentityAndReplacesSteps() {
        AudioProcessingPlan existing = existingPlan();
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportService.getReport(10L)).thenReturn(
                AudioAnalysisReportVO.builder().build());
        when(resultMapper.selectOne(any())).thenReturn(result());
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        when(generator.generate(any())).thenReturn(draft());
        when(planMapper.selectByTaskIdForUpdate(10L))
                .thenReturn(existing);
        when(planMapper.upsert(any())).thenReturn(1);
        when(stepMapper.insertBatch(any())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());

        ProcessingPlanVO first = service.generate(10L);
        ProcessingPlanVO second = service.generate(10L);

        assertEquals(30L, first.getPlanId());
        assertEquals(first.getPlanId(), second.getPlanId());
        assertEquals(2, first.getPlanRevision());
        assertEquals(3, second.getPlanRevision());
        assertEquals(1, first.getStepCount());
        assertEquals(1, first.getSteps().getFirst().getStepOrder());
        verify(planMapper, times(2)).upsert(any());
        verify(stepMapper, times(2)).deleteByPlanId(30L);
        verify(stepMapper, times(2)).insertBatch(any());
        verify(confirmationMapper).markOldDraftsStale(
                eq(30L), eq(2), any());
        verify(confirmationMapper).markOldDraftsStale(
                eq(30L), eq(3), any());
    }

    @Test
    void persistedSnapshotContainsStringIdsAndStructuredParameters()
            throws Exception {
        AudioProcessingPlan existing = existingPlan();
        existing.setId(9_223_372_036_854_775_806L);
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportService.getReport(10L)).thenReturn(
                AudioAnalysisReportVO.builder().build());
        when(resultMapper.selectOne(any())).thenReturn(result());
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        when(generator.generate(any())).thenReturn(draft());
        when(planMapper.selectByTaskIdForUpdate(10L))
                .thenReturn(existing);
        when(planMapper.upsert(any())).thenReturn(1);
        when(stepMapper.insertBatch(any())).thenReturn(1);

        ProcessingPlanVO vo = service.generate(10L);
        String json = objectMapper.writeValueAsString(vo);

        assertTrue(json.contains("\"9223372036854775806\""));
        assertTrue(json.contains("\"parameters\":{")
                && json.contains("\"targetLufs\":-16")
                && json.contains("\"truePeakLimitDbfs\":-1"));
        ArgumentCaptor<AudioProcessingPlan> captor =
                ArgumentCaptor.forClass(AudioProcessingPlan.class);
        verify(planMapper).upsert(captor.capture());
        assertTrue(captor.getValue().getPlanJson()
                .contains("\"parameters\":{")
                && !captor.getValue().getPlanJson()
                .contains("parametersJson"));
    }

    @Test
    void missingPlanReturnsExplicitErrorAndGetHasNoSideEffects() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(planMapper.selectByTaskId(10L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.get(10L));

        assertEquals(ErrorCode.PROCESSING_PLAN_NOT_FOUND.getCode(),
                exception.getCode());
        verify(generator, never()).generate(any());
        verify(planMapper, never()).upsert(any());
    }

    @Test
    void invalidSingleStepParametersDegradeToEmptyObject() {
        AudioProcessingPlan plan = existingPlan();
        AudioProcessingStep step = new AudioProcessingStep();
        step.setId(40L);
        step.setPlanId(30L);
        step.setStepOrder(1);
        step.setOperationType("INCREASE_GAIN");
        step.setParametersJson("not-json");
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(planMapper.selectByTaskId(10L)).thenReturn(plan);
        when(stepMapper.selectByPlanId(30L)).thenReturn(List.of(step));

        ProcessingPlanVO vo = service.get(10L);

        assertEquals(Map.of(), vo.getSteps().getFirst().getParameters());
    }

    @Test
    void generatorFailureDoesNotReplaceExistingPlanSteps() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportService.getReport(10L)).thenReturn(
                AudioAnalysisReportVO.builder().build());
        when(resultMapper.selectOne(any())).thenReturn(result());
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        when(generator.generate(any())).thenThrow(
                new IllegalStateException("rule failure"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.generate(10L));

        assertEquals(ErrorCode.PROCESSING_PLAN_GENERATION_FAILED.getCode(),
                exception.getCode());
        verify(planMapper, never()).upsert(any());
        verify(stepMapper, never()).deleteByPlanId(any());
    }

    @Test
    void unsupportedGeneratedOperationIsNotSaved() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportService.getReport(10L)).thenReturn(
                AudioAnalysisReportVO.builder().build());
        when(resultMapper.selectOne(any())).thenReturn(result());
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        ProcessingStepDraft unsupported = new ProcessingStepDraft(
                ProcessingOperationType.LIMIT_PEAK, "控制过高峰值",
                "description", null, null, null,
                ProcessingPriority.MEDIUM, ProcessingRiskLevel.MEDIUM,
                true, Map.of("truePeakLimitDbfs", -1), "reason");
        when(generator.generate(any())).thenReturn(
                new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                        "summary", 10_000L, List.of(unsupported), 0, 0));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.generate(10L));

        assertEquals(ErrorCode.PROCESSING_PLAN_GENERATION_FAILED.getCode(),
                exception.getCode());
        verify(planMapper, never()).upsert(any());
        verify(stepMapper, never()).deleteByPlanId(any());
    }

    @Test
    void savedStepOrderIsContinuous() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportService.getReport(10L)).thenReturn(
                AudioAnalysisReportVO.builder().build());
        when(resultMapper.selectOne(any())).thenReturn(result());
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        ProcessingStepDraft one = draft().steps().getFirst();
        ProcessingStepDraft two = new ProcessingStepDraft(
                ProcessingOperationType.TRIM_SEGMENT, "裁剪片段",
                "description", 51L, 300L, 400L,
                ProcessingPriority.MEDIUM, ProcessingRiskLevel.MEDIUM,
                true, Map.of(), "reason");
        ProcessingStepDraft three = new ProcessingStepDraft(
                ProcessingOperationType.TRIM_SEGMENT, "裁剪片段",
                "description", 52L, 500L, 600L,
                ProcessingPriority.LOW, ProcessingRiskLevel.MEDIUM,
                true, Map.of(), "reason");
        when(generator.generate(any())).thenReturn(
                new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                        "summary", 10_000L,
                        List.of(one, two, three), 0, 0));
        AudioProcessingPlan plan = existingPlan();
        when(planMapper.selectByTaskIdForUpdate(10L)).thenReturn(plan);
        when(planMapper.upsert(any())).thenReturn(1);
        when(stepMapper.insertBatch(any())).thenReturn(3);

        service.generate(10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioProcessingStep>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(stepMapper).insertBatch(captor.capture());
        assertEquals(List.of(1, 2, 3), captor.getValue().stream()
                .map(AudioProcessingStep::getStepOrder).toList());
    }

    private AudioAnalysisTask task(AnalysisTaskStatus status) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(10L);
        task.setAudioFileId(20L);
        task.setStatus(status);
        return task;
    }

    private AudioAnalysisResult result() {
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setTaskId(10L);
        result.setAudioFileId(20L);
        result.setDurationMs(10_000L);
        return result;
    }

    private AudioProcessingPlan existingPlan() {
        AudioProcessingPlan plan = new AudioProcessingPlan();
        plan.setId(30L);
        plan.setTaskId(10L);
        plan.setAudioFileId(20L);
        plan.setPlanVersion(1);
        plan.setPlanRevision(1);
        plan.setPlanStatus("READY");
        plan.setSummary("old");
        plan.setStepCount(0);
        plan.setPlanJson("{}");
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        return plan;
    }

    private ProcessingPlanDraft draft() {
        ProcessingStepDraft step = new ProcessingStepDraft(
                ProcessingOperationType.NORMALIZE_VOLUME,
                "整段音量标准化", "统一整段音量并控制峰值。", null,
                null, null, ProcessingPriority.MEDIUM,
                ProcessingRiskLevel.MEDIUM, true,
                Map.of("targetLufs", -16,
                        "truePeakLimitDbfs", -1), "整体音量偏差。");
        return new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                "summary", 10_000L, List.of(step), 0, 0);
    }
}
