package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisReport;
import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisReportMapper;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.report.AudioAnalysisReportGenerator;
import com.audioagent.analysis.report.QualityGrade;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioAnalysisReportServiceImplTest {

    private AudioAnalysisTaskMapper taskMapper;
    private AudioAnalysisResultMapper resultMapper;
    private AudioIssueSegmentMapper issueMapper;
    private AudioAnalysisReportMapper reportMapper;
    private AudioFileMapper fileMapper;
    private AudioAnalysisReportGenerator generator;
    private AnalysisProperties properties;
    private ObjectMapper objectMapper;
    private AudioAnalysisReportServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        resultMapper = mock(AudioAnalysisResultMapper.class);
        issueMapper = mock(AudioIssueSegmentMapper.class);
        reportMapper = mock(AudioAnalysisReportMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        generator = mock(AudioAnalysisReportGenerator.class);
        properties = new AnalysisProperties();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AudioAnalysisReportServiceImpl(taskMapper,
                resultMapper, issueMapper, reportMapper, fileMapper,
                generator, properties, objectMapper);
    }

    @Test
    void processingAndFailedTasksReturnReportNotReady() {
        for (AnalysisTaskStatus status : List.of(
                AnalysisTaskStatus.PROCESSING, AnalysisTaskStatus.FAILED)) {
            when(taskMapper.selectById(10L)).thenReturn(task(status));
            BusinessException exception = assertThrows(
                    BusinessException.class, () -> service.getReport(10L));
            assertEquals(ErrorCode.REPORT_NOT_READY.getCode(),
                    exception.getCode());
        }
    }

    @Test
    void successfulLegacyTaskIsLazilyGeneratedAndSaved() throws Exception {
        AudioAnalysisTask task = task(AnalysisTaskStatus.SUCCESS);
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setTaskId(10L);
        result.setAudioFileId(20L);
        AudioAnalysisReportVO.Payload payload = payload();
        AudioAnalysisReportGenerator.GeneratedReport generated =
                new AudioAnalysisReportGenerator.GeneratedReport(100,
                        QualityGrade.EXCELLENT, "summary", payload);
        AudioAnalysisReport saved = savedReport(payload);

        when(taskMapper.selectById(10L)).thenReturn(task);
        when(reportMapper.selectByTaskId(10L)).thenReturn(null, saved);
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        when(generator.generate(any(), any(), any())).thenReturn(generated);
        when(reportMapper.upsert(any())).thenReturn(1);

        AudioAnalysisReportVO report = service.getReport(10L);

        assertEquals(100, report.getQualityScore());
        assertEquals("10", String.valueOf(report.getTaskId()));
        assertEquals(0, report.getIssueSummary().getTotalIssueCount());
        verify(reportMapper).upsert(any());
    }

    @Test
    void retryGenerationUsesUpsertAndKeepsOneTaskReportIdentity() {
        AudioAnalysisTask task = task(AnalysisTaskStatus.PROCESSING);
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setTaskId(10L);
        result.setAudioFileId(20L);
        AudioAnalysisReportVO.Payload payload = payload();
        AudioAnalysisReportGenerator.GeneratedReport generated =
                new AudioAnalysisReportGenerator.GeneratedReport(90,
                        QualityGrade.EXCELLENT, "summary", payload);
        AudioAnalysisReport saved = savedReport(payload);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(result);
        when(issueMapper.selectByTask(10L)).thenReturn(List.of());
        when(generator.generate(any(), any(), any())).thenReturn(generated);
        when(reportMapper.upsert(any())).thenReturn(1);
        when(reportMapper.selectByTaskId(10L)).thenReturn(saved);

        AudioAnalysisReportVO first = service.generateAndSave(10L, 20L);
        AudioAnalysisReportVO second = service.generateAndSave(10L, 20L);

        assertEquals(first.getReportId(), second.getReportId());
        verify(reportMapper, times(2)).upsert(any());
    }

    @Test
    void unknownTaskReturnsExplicitTaskError() {
        when(taskMapper.selectById(999L)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getReport(999L));
        assertEquals(ErrorCode.AUDIO_TASK_NOT_FOUND.getCode(),
                exception.getCode());
    }

    @Test
    void malformedPersistedJsonReturnsReportParseFailed() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        AudioAnalysisReport report = savedReport(payload());
        report.setReportJson("not-json");
        when(reportMapper.selectByTaskId(10L)).thenReturn(report);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getReport(10L));
        assertEquals(ErrorCode.REPORT_PARSE_FAILED.getCode(),
                exception.getCode());
    }

    @Test
    void disabledReportDoesNotWriteAndLeavesAnalysisFlowUnblocked() {
        properties.getReport().setEnabled(false);
        assertNull(service.generateAndSave(10L, 20L));
        verify(taskMapper, never()).selectById(any());
        verify(reportMapper, never()).upsert(any());
    }

    @Test
    void missingAnalysisResultReturnsDataIncomplete() {
        when(taskMapper.selectById(10L)).thenReturn(
                task(AnalysisTaskStatus.SUCCESS));
        when(reportMapper.selectByTaskId(10L)).thenReturn(null);
        when(resultMapper.selectOne(any())).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getReport(10L));
        assertEquals(ErrorCode.REPORT_DATA_INCOMPLETE.getCode(),
                exception.getCode());
    }

    private AudioAnalysisTask task(AnalysisTaskStatus status) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(10L);
        task.setAudioFileId(20L);
        task.setStatus(status);
        return task;
    }

    private AudioAnalysisReportVO.Payload payload() {
        return AudioAnalysisReportVO.Payload.builder()
                .audioOverview(AudioAnalysisReportVO.AudioOverview.builder()
                        .fileName("old.wav").build())
                .issueSummary(AudioAnalysisReportVO.IssueSummary.builder()
                        .totalIssueCount(0)
                        .totalIssueDurationMs(0L)
                        .silenceCount(0).volumeDropCount(0)
                        .volumeSpikeCount(0).noiseRiskCount(0)
                        .silenceDurationMs(0L).volumeIssueDurationMs(0L)
                        .noiseRiskDurationMs(0L).build())
                .keyIssues(List.of()).timeline(List.of())
                .recommendations(List.of()).build();
    }

    private AudioAnalysisReport savedReport(
            AudioAnalysisReportVO.Payload payload) {
        AudioAnalysisReport report = new AudioAnalysisReport();
        report.setId(30L);
        report.setTaskId(10L);
        report.setAudioFileId(20L);
        report.setReportVersion("1.0");
        report.setQualityScore(100);
        report.setQualityGrade("EXCELLENT");
        report.setSummary("summary");
        try {
            report.setReportJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }
}
