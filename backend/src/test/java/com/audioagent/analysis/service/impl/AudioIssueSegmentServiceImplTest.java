package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.noise.NoiseRiskOverlapFilter;
import com.audioagent.analysis.noise.NoiseRiskSegment;
import com.audioagent.analysis.service.AudioIssueSegmentService.IssueStatistics;
import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.analysis.vo.IssueSummaryVO;
import com.audioagent.analysis.volume.VolumeIssueSegment;
import com.audioagent.analysis.volume.VolumeIssueSeverityEvaluator;
import com.audioagent.analysis.volume.VolumeIssueSilenceFilter;
import com.audioagent.analysis.volume.VolumeIssueType;
import com.audioagent.analysis.volume.VolumeSampleWindow;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioIssueSegmentServiceImplTest {

    private AudioIssueSegmentMapper issueMapper;
    private AudioAnalysisTaskMapper taskMapper;
    private AnalysisProperties properties;
    private AudioIssueSegmentServiceImpl service;
    private AudioResourceOwnershipService ownershipService;

    @BeforeEach
    void setUp() {
        issueMapper = mock(AudioIssueSegmentMapper.class);
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        properties = new AnalysisProperties();
        ownershipService = mock(AudioResourceOwnershipService.class);
        service = new AudioIssueSegmentServiceImpl(issueMapper, taskMapper,
                properties, new ObjectMapper(),
                new VolumeIssueSilenceFilter(properties),
                new VolumeIssueSeverityEvaluator(properties),
                new NoiseRiskOverlapFilter(properties), ownershipService);
        when(issueMapper.insertBatch(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation
                        .getArgument(0)).size());
        when(issueMapper.selectByTaskAndType(anyLong(), anyString()))
                .thenReturn(List.of());
    }

    @Test
    void retryReplacesOldSegmentsWithoutCreatingDuplicates() {
        List<SilenceSegment> first = List.of(
                new SilenceSegment(0, 2_000, 2_000));
        List<SilenceSegment> retry = List.of(
                new SilenceSegment(1_000, 5_000, 4_000),
                new SilenceSegment(8_000, 17_000, 9_000));

        service.replaceSilenceSegments(11L, 22L, first);
        IssueStatistics statistics = service.replaceSilenceSegments(
                11L, 22L, retry);

        verify(issueMapper, times(2))
                .deleteByTaskAndType(11L, "SILENCE");
        verify(issueMapper, times(2)).insertBatch(anyList());
        assertEquals(2, statistics.issueCount());
        assertEquals(13_000, statistics.totalSilenceDurationMs());
    }

    @Test
    void assignsConfiguredSeverityBoundaries() {
        List<SilenceSegment> segments = List.of(
                new SilenceSegment(0, 2_000, 2_000),
                new SilenceSegment(3_000, 6_000, 3_000),
                new SilenceSegment(8_000, 16_000, 8_000));

        service.replaceSilenceSegments(11L, 22L, segments);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioIssueSegment>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(issueMapper).insertBatch(captor.capture());
        assertEquals(List.of("LOW", "MEDIUM", "HIGH"),
                captor.getValue().stream()
                        .map(AudioIssueSegment::getSeverity)
                        .toList());
    }

    @Test
    void queryReturnsAscendingRecordsAndSummary() {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(11L);
        task.setAudioFileId(22L);
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(issueMapper.selectByTask(11L))
                .thenReturn(new ArrayList<>(List.of(
                        issue(3L, "VOLUME_SPIKE", 5_000, 6_500),
                        issue(1L, "SILENCE", 0, 1_000),
                        issue(2L, "VOLUME_DROP", 2_000, 4_000))));

        IssueSummaryVO result = service.getIssues(7L, 11L, null);

        assertEquals(3, result.getIssueCount());
        assertEquals(4_500, result.getTotalIssueDurationMs());
        assertEquals(result.getIssueCount(), result.getRecords().size());
        assertEquals(List.of(0L, 2_000L, 5_000L),
                result.getRecords().stream()
                .map(record -> record.getStartMs()).toList());
        assertEquals(List.of("SILENCE", "VOLUME_DROP", "VOLUME_SPIKE"),
                result.getRecords().stream()
                        .map(record -> record.getIssueType()).toList());
        assertEquals(2, result.getVolumeIssueCount());
        verify(issueMapper).selectByTask(11L);
        verify(issueMapper, never()).selectByTaskAndType(
                anyLong(), anyString());
    }

    @Test
    void queryReturnsEmptyRecordsNormally() {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(11L);
        task.setAudioFileId(22L);
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(issueMapper.selectByTask(11L))
                .thenReturn(List.of());

        IssueSummaryVO result = service.getIssues(7L, 11L, "silence");

        assertEquals(0, result.getIssueCount());
        assertEquals(0, result.getTotalIssueDurationMs());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getVolumeDropCount());
        assertEquals(0, result.getVolumeSpikeCount());
        assertEquals(0, result.getTotalVolumeIssueDurationMs());
        assertEquals(0, result.getNoiseRiskCount());
        assertEquals(0, result.getTotalNoiseRiskDurationMs());
    }

    @Test
    void querySupportsVolumeTypeAndReturnsVolumeSummaryCounts() {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(11L);
        task.setAudioFileId(22L);
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(issueMapper.selectByTask(11L)).thenReturn(List.of(
                issue(1L, "SILENCE", 0, 1_000),
                issue(2L, "VOLUME_DROP", 2_000, 4_000),
                issue(3L, "VOLUME_SPIKE", 5_000, 6_500),
                issue(4L, "NOISE_RISK", 7_000, 9_000)));

        IssueSummaryVO result = service.getIssues(
                7L, 11L, "volume_drop");

        assertEquals(1, result.getIssueCount());
        assertEquals("VOLUME_DROP",
                result.getRecords().getFirst().getIssueType());
        assertEquals(2, result.getVolumeIssueCount());
        assertEquals(1, result.getVolumeDropCount());
        assertEquals(1, result.getVolumeSpikeCount());
        assertEquals(3_500, result.getTotalVolumeIssueDurationMs());
        assertEquals(2_000, result.getTotalIssueDurationMs());
    }

    @Test
    void blankIssueTypeUsesSameNoFilterSemantics() {
        AudioAnalysisTask task = task(11L, 22L);
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(issueMapper.selectByTask(11L)).thenReturn(List.of(
                issue(1L, "SILENCE", 0, 1_000),
                issue(2L, "VOLUME_DROP", 2_000, 4_000)));

        IssueSummaryVO result = service.getIssues(7L, 11L, "   ");

        assertEquals(2, result.getIssueCount());
        assertEquals(2, result.getRecords().size());
        assertEquals(3_000, result.getTotalIssueDurationMs());
    }

    @Test
    void filtersEachSupportedIssueType() {
        AudioAnalysisTask task = task(11L, 22L);
        when(taskMapper.selectById(11L)).thenReturn(task);
        when(issueMapper.selectByTask(11L)).thenReturn(List.of(
                issue(1L, "SILENCE", 0, 1_000),
                issue(2L, "VOLUME_DROP", 2_000, 4_000),
                issue(3L, "VOLUME_SPIKE", 5_000, 6_500),
                issue(4L, "NOISE_RISK", 7_000, 9_000)));

        for (String issueType : List.of(
                "SILENCE", "VOLUME_DROP", "VOLUME_SPIKE", "NOISE_RISK")) {
            IssueSummaryVO result = service.getIssues(7L, 11L, issueType);
            assertEquals(1, result.getIssueCount());
            assertEquals(issueType,
                    result.getRecords().getFirst().getIssueType());
        }
    }

    @Test
    void unsupportedIssueTypeReturnsParameterError() {
        when(taskMapper.selectById(11L)).thenReturn(task(11L, 22L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getIssues(7L, 11L, "UNKNOWN_TYPE"));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains(
                "Unsupported issue type"));
        verify(issueMapper, never()).selectByTask(anyLong());
    }

    @Test
    void metricsAreStructuredAndInvalidJsonFallsBackToEmptyObject() {
        when(taskMapper.selectById(11L)).thenReturn(task(11L, 22L));
        AudioIssueSegment valid = issue(1L, "VOLUME_DROP", 0, 1_000);
        valid.setMetricJson("{\"deviationLu\":10.8,\"sampleCount\":5}");
        AudioIssueSegment invalid = issue(2L, "VOLUME_SPIKE",
                2_000, 3_000);
        invalid.setMetricJson("{invalid-json");
        when(issueMapper.selectByTask(11L)).thenReturn(
                List.of(valid, invalid));

        IssueSummaryVO result = service.getIssues(7L, 11L, null);

        assertEquals(10.8, result.getRecords().get(0).getMetrics()
                .get("deviationLu"));
        assertEquals(5, result.getRecords().get(0).getMetrics()
                .get("sampleCount"));
        assertTrue(result.getRecords().get(1).getMetrics().isEmpty());
    }

    @Test
    void issueIdSerializesAsStringWithoutPrecisionLoss() throws Exception {
        long largeId = 9_007_199_254_740_993L;
        when(taskMapper.selectById(11L)).thenReturn(task(11L, 22L));
        when(issueMapper.selectByTask(11L)).thenReturn(List.of(
                issue(largeId, "SILENCE", 0, 1_000)));

        String json = new ObjectMapper().writeValueAsString(
                service.getIssues(7L, 11L, null));

        assertTrue(json.contains(
                "\"issueId\":\"9007199254740993\""));
    }

    @Test
    void volumeRetryReplacesOnlyVolumeTypesAndBuildsMetricJson() {
        when(issueMapper.selectByTaskAndType(11L, "SILENCE"))
                .thenReturn(List.of());
        VolumeIssueSegment segment = new VolumeIssueSegment(
                VolumeIssueType.VOLUME_DROP, 0, 2_000,
                List.of(
                        new VolumeSampleWindow(
                                VolumeIssueType.VOLUME_DROP,
                                0, 1_000, new BigDecimal("-30.00")),
                        new VolumeSampleWindow(
                                VolumeIssueType.VOLUME_DROP,
                                1_000, 2_000,
                                new BigDecimal("-28.00"))));

        service.replaceVolumeSegments(11L, 22L, List.of(segment),
                new BigDecimal("-18.00"), 10_000);
        service.replaceVolumeSegments(11L, 22L, List.of(segment),
                new BigDecimal("-18.00"), 10_000);

        verify(issueMapper, times(2)).deleteVolumeTypesByTask(11L);
        verify(issueMapper, never()).deleteByTaskAndType(11L, "SILENCE");
        verify(issueMapper, times(2)).insertBatch(anyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioIssueSegment>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(issueMapper, times(2)).insertBatch(captor.capture());
        AudioIssueSegment saved = captor.getAllValues().getLast().getFirst();
        assertEquals("VOLUME_DROP", saved.getIssueType());
        assertTrue(saved.getMetricJson().contains(
                "\"baselineIntegratedLufs\":-18.00"));
        assertTrue(saved.getMetricJson().contains(
                "\"averageMomentaryLufs\":-29.00"));
        assertTrue(saved.getMetricJson().contains(
                "\"sampleCount\":2"));
    }

    @Test
    void noiseRetryReplacesOnlyNoiseRiskAndBuildsMetricJson() {
        NoiseRiskSegment segment = noiseSegment();

        service.replaceNoiseRiskSegments(11L, 22L, List.of(segment));
        service.replaceNoiseRiskSegments(11L, 22L, List.of(segment));

        verify(issueMapper, times(2))
                .deleteByTaskAndType(11L, "NOISE_RISK");
        verify(issueMapper, never()).deleteByTaskAndType(11L, "SILENCE");
        verify(issueMapper, never()).deleteVolumeTypesByTask(11L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioIssueSegment>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(issueMapper, times(2)).insertBatch(captor.capture());
        AudioIssueSegment saved = captor.getAllValues().getLast().getFirst();
        assertEquals("NOISE_RISK", saved.getIssueType());
        assertEquals("MEDIUM", saved.getSeverity());
        assertTrue(saved.getDescription().contains("疑似"));
        assertTrue(saved.getMetricJson().contains(
                "\"noiseRiskScore\":0.7300"));
        assertTrue(saved.getMetricJson().contains(
                "\"detector\":\"FFMPEG_SPECTRAL_HEURISTIC\""));
        assertTrue(!saved.getMetricJson().contains("frames"));
    }

    @Test
    void queryNoiseRiskReturnsFilteredCountAndGlobalNoiseSummary() {
        when(taskMapper.selectById(11L)).thenReturn(task(11L, 22L));
        when(issueMapper.selectByTask(11L)).thenReturn(List.of(
                issue(1L, "SILENCE", 0, 1_000),
                issue(2L, "NOISE_RISK", 2_000, 5_000),
                issue(3L, "NOISE_RISK", 6_000, 8_000)));

        IssueSummaryVO result = service.getIssues(7L, 11L, "noise_risk");

        assertEquals(2, result.getIssueCount());
        assertEquals(2, result.getNoiseRiskCount());
        assertEquals(5_000, result.getTotalNoiseRiskDurationMs());
        assertTrue(result.getRecords().stream().allMatch(
                record -> "NOISE_RISK".equals(record.getIssueType())));
    }

    @Test
    void silenceOverlapPreventsNoiseRiskPersistence() {
        when(issueMapper.selectByTaskAndType(11L, "SILENCE"))
                .thenReturn(List.of(issue(1L, "SILENCE", 0, 1_000)));

        var stats = service.replaceNoiseRiskSegments(
                11L, 22L, List.of(noiseSegment()));

        assertEquals(0, stats.issueCount());
        verify(issueMapper).deleteByTaskAndType(11L, "NOISE_RISK");
        verify(issueMapper, never()).insertBatch(anyList());
    }

    @Test
    void foreignTaskCannotReadIssuesInsideServiceBoundary() {
        doThrow(new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                "资源不存在或不可访问"))
                .when(ownershipService).requireTaskOwned(7L, 11L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getIssues(7L, 11L, null));

        assertEquals(ErrorCode.AUDIO_TASK_NOT_FOUND.getCode(),
                error.getCode());
        verify(issueMapper, never()).selectByTask(anyLong());
    }

    private NoiseRiskSegment noiseSegment() {
        return new NoiseRiskSegment(0, 2_000, 2_000,
                new BigDecimal("-32.40"), new BigDecimal("0.61"),
                new BigDecimal("0.78"), new BigDecimal("2.10"),
                new BigDecimal("0.7300"), new BigDecimal("0.8100"),
                "MEDIUM", 20, "MEDIUM");
    }

    private AudioIssueSegment issue(Long id, long startMs, long endMs) {
        return issue(id, "SILENCE", startMs, endMs);
    }

    private AudioAnalysisTask task(Long taskId, Long audioFileId) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(taskId);
        task.setAudioFileId(audioFileId);
        return task;
    }

    private AudioIssueSegment issue(Long id, String issueType,
                                    long startMs, long endMs) {
        AudioIssueSegment issue = new AudioIssueSegment();
        issue.setId(id);
        issue.setIssueType(issueType);
        issue.setStartMs(startMs);
        issue.setEndMs(endMs);
        issue.setDurationMs(endMs - startMs);
        issue.setSeverity("LOW");
        issue.setDescription("silence");
        issue.setMetricJson("{}");
        return issue;
    }
}
