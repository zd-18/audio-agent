package com.audioagent.analysis.executor;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessAnalyzer;
import com.audioagent.analysis.loudness.LoudnessAnalysis;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.noise.NoiseRiskAnalyzer;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.analysis.service.AudioIssueSegmentService;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.service.AudioIssueSegmentService.IssueStatistics;
import com.audioagent.analysis.service.AudioIssueSegmentService.VolumeIssueStatistics;
import com.audioagent.analysis.service.AudioIssueSegmentService.NoiseIssueStatistics;
import com.audioagent.analysis.silence.SilenceDetector;
import com.audioagent.analysis.volume.VolumeIssueAnalyzer;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioAnalysisTaskExecutorTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, AudioAnalysisTask.class);
    }

    private AudioAnalysisTaskMapper taskMapper;
    private AudioFileMapper fileMapper;
    private AudioAnalysisResultMapper resultMapper;
    private MinioStorageService storageService;
    private AudioMetadataProbe metadataProbe;
    private SilenceDetector silenceDetector;
    private LoudnessAnalyzer loudnessAnalyzer;
    private VolumeIssueAnalyzer volumeIssueAnalyzer;
    private NoiseRiskAnalyzer noiseRiskAnalyzer;
    private AudioIssueSegmentService issueService;
    private AudioAnalysisReportService reportService;
    private AudioProcessingPlanService processingPlanService;
    private AnalysisProperties properties;
    private AudioAnalysisTaskExecutor executor;

    @BeforeEach
    void setUp() {
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        resultMapper = mock(AudioAnalysisResultMapper.class);
        storageService = mock(MinioStorageService.class);
        metadataProbe = mock(AudioMetadataProbe.class);
        silenceDetector = mock(SilenceDetector.class);
        loudnessAnalyzer = mock(LoudnessAnalyzer.class);
        volumeIssueAnalyzer = mock(VolumeIssueAnalyzer.class);
        noiseRiskAnalyzer = mock(NoiseRiskAnalyzer.class);
        issueService = mock(AudioIssueSegmentService.class);
        reportService = mock(AudioAnalysisReportService.class);
        processingPlanService = mock(AudioProcessingPlanService.class);
        TransactionTemplate transactionTemplate =
                mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        properties = new AnalysisProperties();
        LoudnessEvaluator loudnessEvaluator =
                new LoudnessEvaluator(properties);
        executor = new AudioAnalysisTaskExecutor(taskMapper, fileMapper,
                resultMapper, storageService, metadataProbe,
                silenceDetector, loudnessAnalyzer, loudnessEvaluator,
                volumeIssueAnalyzer, noiseRiskAnalyzer, issueService,
                reportService, processingPlanService, properties,
                transactionTemplate);
    }

    @Test
    void fullAnalysisKeepsMetadataAndSucceedsWhenNoSilenceExists() {
        AudioFile audioFile = new AudioFile();
        audioFile.setId(22L);
        audioFile.setObjectKey("uploads/audio.wav");
        audioFile.setExtension("wav");
        audioFile.setUserId(7L);
        audioFile.setDeleted(0);
        AudioMetadata metadata = AudioMetadata.builder()
                .formatName("wav")
                .codecName("pcm_s16le")
                .durationMs(10_000L)
                .sampleRate(48_000)
                .channels(2)
                .bitRate(1_536_000L)
                .fileSize(1_920_000L)
                .build();

        when(taskMapper.update(any(), any())).thenReturn(1);
        when(fileMapper.selectById(22L)).thenReturn(audioFile);
        when(storageService.getObject("uploads/audio.wav"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(metadataProbe.probe(any())).thenReturn(metadata);
        when(silenceDetector.detect(any(),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(List.of());
        when(loudnessAnalyzer.analyze(any())).thenReturn(Optional.of(
                new LoudnessAnalysis(new LoudnessMetrics(
                        new BigDecimal("-18.40"),
                        new BigDecimal("6.20"),
                        new BigDecimal("-1.50"),
                        new BigDecimal("-1.20")), List.of())));
        when(volumeIssueAnalyzer.analyze(any(),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(List.of());
        when(issueService.replaceSilenceSegments(11L, 22L, List.of()))
                .thenReturn(new IssueStatistics(0, 0, 0));
        when(issueService.replaceVolumeSegments(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(22L), any(),
                org.mockito.ArgumentMatchers.eq(
                        new BigDecimal("-18.40")),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(new VolumeIssueStatistics(0, 0, 0, 0));
        when(noiseRiskAnalyzer.analyze(any(),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(List.of());
        when(issueService.replaceNoiseRiskSegments(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(22L), any()))
                .thenReturn(new NoiseIssueStatistics(0, 0));
        when(resultMapper.upsert(any())).thenReturn(1);
        when(processingPlanService.generateForOwner(7L, 11L)).thenThrow(
                new IllegalStateException("plan generation failed"));

        executor.execute(11L, 22L);

        ArgumentCaptor<AudioAnalysisResult> resultCaptor =
                ArgumentCaptor.forClass(AudioAnalysisResult.class);
        verify(resultMapper).upsert(resultCaptor.capture());
        AudioAnalysisResult result = resultCaptor.getValue();
        assertEquals("wav", result.getFormatName());
        assertEquals("pcm_s16le", result.getCodecName());
        assertEquals(10_000L, result.getDurationMs());
        assertEquals(0, result.getIssueCount());
        assertEquals(0, result.getSilenceCount());
        assertEquals(0L, result.getTotalSilenceDurationMs());
        assertEquals(BigDecimal.ZERO, result.getSilenceRatio());
        assertEquals(new BigDecimal("-18.40"),
                result.getIntegratedLoudnessLufs());
        assertEquals(new BigDecimal("6.20"),
                result.getLoudnessRangeLu());
        assertEquals(new BigDecimal("-1.50"),
                result.getSamplePeakDbfs());
        assertEquals(new BigDecimal("-1.20"),
                result.getTruePeakDbfs());
        verify(issueService).replaceSilenceSegments(11L, 22L, List.of());
        verify(reportService).generateAndSave(11L, 22L);
        verify(processingPlanService).generateForOwner(7L, 11L);
        verify(taskMapper, times(2)).update(any(), any());
        org.mockito.InOrder order = inOrder(taskMapper, reportService);
        order.verify(taskMapper).update(any(), any());
        order.verify(reportService).generateAndSave(11L, 22L);
        order.verify(taskMapper).update(any(), any());
    }

    @Test
    void disabledProcessingPlanDoesNotAffectFullAnalysisSuccess() {
        properties.getProcessingPlan().setEnabled(false);
        stubSuccessfulAnalysisWithoutIssues();

        executor.execute(11L, 22L);

        verify(reportService).generateAndSave(11L, 22L);
        verify(processingPlanService,
                org.mockito.Mockito.never()).generateForOwner(any(), any());
        verify(taskMapper, times(2)).update(any(), any());
    }

    @Test
    void concurrentPendingDeliveriesAllowOnlyOneAnalysisExecution()
            throws Exception {
        properties.getProcessingPlan().setEnabled(false);
        stubSuccessfulAnalysisWithoutIssues();
        CountDownLatch firstExecutionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstExecution = new CountDownLatch(1);
        when(storageService.getObject("uploads/audio.wav"))
                .thenAnswer(invocation -> {
                    firstExecutionStarted.countDown();
                    assertTrue(releaseFirstExecution.await(
                            5, TimeUnit.SECONDS));
                    return new ByteArrayInputStream(new byte[]{1});
                });
        AtomicInteger updateCalls = new AtomicInteger();
        when(taskMapper.update(any(), any())).thenAnswer(invocation -> {
            int call = updateCalls.incrementAndGet();
            return call == 2 ? 0 : 1;
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> winner = pool.submit(
                    () -> executor.execute(11L, 22L));
            assertTrue(firstExecutionStarted.await(5, TimeUnit.SECONDS));

            AudioAnalysisException loser = assertThrows(
                    AudioAnalysisException.class,
                    () -> executor.execute(11L, 22L));
            assertEquals(AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED,
                    loser.getErrorCode());

            releaseFirstExecution.countDown();
            winner.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstExecution.countDown();
            pool.shutdownNow();
        }

        verify(storageService, times(1)).getObject("uploads/audio.wav");
        verify(resultMapper, times(1)).upsert(any());
    }

    private void stubSuccessfulAnalysisWithoutIssues() {
        AudioFile audioFile = new AudioFile();
        audioFile.setId(22L);
        audioFile.setObjectKey("uploads/audio.wav");
        audioFile.setExtension("wav");
        audioFile.setUserId(7L);
        audioFile.setDeleted(0);
        AudioMetadata metadata = AudioMetadata.builder()
                .formatName("wav")
                .codecName("pcm_s16le")
                .durationMs(10_000L)
                .sampleRate(48_000)
                .channels(2)
                .bitRate(1_536_000L)
                .fileSize(1_920_000L)
                .build();
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(fileMapper.selectById(22L)).thenReturn(audioFile);
        when(storageService.getObject("uploads/audio.wav"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(metadataProbe.probe(any())).thenReturn(metadata);
        when(silenceDetector.detect(any(),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(List.of());
        when(loudnessAnalyzer.analyze(any())).thenReturn(Optional.empty());
        when(issueService.replaceSilenceSegments(11L, 22L, List.of()))
                .thenReturn(new IssueStatistics(0, 0, 0));
        when(issueService.replaceVolumeSegments(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(22L), any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(10_000L)))
                .thenReturn(new VolumeIssueStatistics(0, 0, 0, 0));
        when(issueService.replaceNoiseRiskSegments(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(22L), any()))
                .thenReturn(new NoiseIssueStatistics(0, 0));
        when(resultMapper.upsert(any())).thenReturn(1);
    }
}
