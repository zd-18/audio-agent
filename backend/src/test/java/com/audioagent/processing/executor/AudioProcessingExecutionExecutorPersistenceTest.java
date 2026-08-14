package com.audioagent.processing.executor;

import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileRole;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.AudioVersionSummaryBuilder;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.exception.ProcessingExecutionErrorClassifier;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.pipeline.AudioProcessingPipeline;
import com.audioagent.processing.pipeline.ProcessingOutput;
import com.audioagent.processing.pipeline.ProcessingOutputValidator;
import com.audioagent.processing.critic.ProcessingResultCritic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingExecutionExecutorPersistenceTest {

    private static final long EXECUTION_ID = 90L;
    private static final byte[] RESULT_BYTES =
            "processed-audio-result".getBytes();

    @TempDir
    Path tempDirectory;

    private AudioProcessingExecutionMapper executionMapper;
    private AudioProcessingExecutionStepMapper stepMapper;
    private AudioFileMapper fileMapper;
    private MinioStorageService storageService;
    private AudioProcessingPipeline pipeline;
    private AudioMetadataProbe metadataProbe;
    private ProcessingOutputValidator outputValidator;
    private ProcessingResultCritic resultCritic;
    private ProcessingExecutionWorkDirectory workDirectories;
    private AudioProcessingExecutionExecutor executor;
    private AudioProcessingExecution execution;
    private AudioFile source;
    private Path workDirectory;

    @BeforeEach
    void setUp() throws Exception {
        executionMapper = mock(AudioProcessingExecutionMapper.class);
        stepMapper = mock(AudioProcessingExecutionStepMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        storageService = mock(MinioStorageService.class);
        pipeline = mock(AudioProcessingPipeline.class);
        metadataProbe = mock(AudioMetadataProbe.class);
        outputValidator = mock(ProcessingOutputValidator.class);
        resultCritic = mock(ProcessingResultCritic.class);
        workDirectories = mock(ProcessingExecutionWorkDirectory.class);
        TransactionTemplate transactionTemplate =
                mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setBucketName("result-bucket");
        executor = new AudioProcessingExecutionExecutor(executionMapper,
                stepMapper, fileMapper, new AudioVersionSummaryBuilder(),
                storageService, minioProperties,
                pipeline, metadataProbe, outputValidator, resultCritic,
                workDirectories,
                new ProcessingExecutionErrorClassifier(),
                new ObjectMapper(), transactionTemplate);

        execution = execution();
        source = source();
        workDirectory = tempDirectory.resolve("work");
        Files.createDirectories(workDirectory);
        when(executionMapper.claim(eq(EXECUTION_ID), any()))
                .thenReturn(1);
        when(executionMapper.selectExecutionById(EXECUTION_ID))
                .thenReturn(execution);
        when(fileMapper.selectById(source.getId())).thenReturn(source);
        when(fileMapper.selectBySourceExecutionId(EXECUTION_ID))
                .thenReturn(null);
        when(fileMapper.selectByIdForUpdate(source.getRootAudioFileId()))
                .thenReturn(source);
        when(fileMapper.selectNextVersionNo(source.getRootAudioFileId()))
                .thenReturn(1);
        when(workDirectories.prepare(EXECUTION_ID))
                .thenReturn(workDirectory);
        when(storageService.getObject("source-bucket", "original/key.wav"))
                .thenReturn(new ByteArrayInputStream("source".getBytes()));
        when(stepMapper.selectByExecutionId(EXECUTION_ID))
                .thenReturn(List.of(executableStep()));
        when(stepMapper.markExecutableProcessing(eq(EXECUTION_ID), any()))
                .thenReturn(1);
        when(pipeline.execute(any(Path.class), anyList(),
                eq(workDirectory), any())).thenAnswer(invocation -> {
                    Path output = workDirectory.resolve("result.wav");
                    Files.write(output, RESULT_BYTES);
                    return new ProcessingOutput(output, 1_000L);
                });
        when(metadataProbe.probe(any(Path.class))).thenReturn(
                AudioMetadata.builder()
                        .durationMs(1_000L)
                        .sampleRate(48_000)
                        .channels(2)
                        .bitRate(1_536_000L)
                        .build());
        when(outputValidator.durationToleranceMs()).thenReturn(1_000L);
        doAnswer(invocation -> {
            InputStream input = invocation.getArgument(1);
            input.transferTo(java.io.OutputStream.nullOutputStream());
            return null;
        }).when(storageService).upload(
                eq("repair/7/90/result.wav"), any(InputStream.class),
                eq((long) RESULT_BYTES.length), eq("audio/wav"));
        when(fileMapper.insert(any(AudioFile.class))).thenReturn(1);
        when(executionMapper.complete(eq(EXECUTION_ID), any(), any()))
                .thenReturn(1);
        when(stepMapper.markProcessingSuccess(eq(EXECUTION_ID), any()))
                .thenReturn(1);
    }

    @Test
    void successPersistsIndependentRepairResultAndCleansWorkFiles()
            throws Exception {
        String originalObjectKey = source.getObjectKey();
        FileRole originalRole = source.getFileRole();

        executor.execute(EXECUTION_ID);

        ArgumentCaptor<AudioFile> fileCaptor =
                ArgumentCaptor.forClass(AudioFile.class);
        verify(fileMapper).insert(fileCaptor.capture());
        AudioFile result = fileCaptor.getValue();
        assertEquals(source.getId(), result.getSourceFileId());
        assertEquals(source.getRootAudioFileId(),
                result.getRootAudioFileId());
        assertEquals(1, result.getVersionNo());
        assertEquals("裁剪片段", result.getVersionSummary());
        assertEquals(EXECUTION_ID, result.getSourceExecutionId());
        assertEquals(source.getUserId(), result.getUserId());
        assertEquals(FileRole.REPAIR_RESULT, result.getFileRole());
        assertEquals(FileStatus.AVAILABLE, result.getFileStatus());
        assertEquals("result-bucket", result.getBucketName());
        assertEquals("repair/7/90/result.wav", result.getObjectKey());
        assertEquals("interview-repaired-90.wav",
                result.getOriginalName());
        assertEquals("wav", result.getExtension());
        assertEquals("audio/wav", result.getMimeType());
        assertEquals((long) RESULT_BYTES.length, result.getSizeBytes());
        assertEquals(sha256(RESULT_BYTES), result.getSha256());
        assertEquals(1_000L, result.getDurationMs());
        assertEquals(48_000, result.getSampleRate());
        assertEquals(2, result.getChannels());
        assertEquals(1_536_000, result.getBitRate());
        assertEquals(0, result.getDeleted());
        verify(executionMapper).complete(EXECUTION_ID, result.getId(),
                result.getCreatedAt());
        verify(stepMapper).markProcessingSuccess(eq(EXECUTION_ID), any());
        verify(storageService, never()).delete(any());
        verify(workDirectories).cleanQuietly(EXECUTION_ID);
        verify(outputValidator).durationToleranceMs();
        verify(resultCritic).review(any(Path.class),
                any(AudioMetadata.class), eq(1_000L), any(), eq(1));

        assertEquals(originalObjectKey, source.getObjectKey());
        assertEquals(originalRole, source.getFileRole());
        assertEquals(FileRole.ORIGINAL, source.getFileRole());
    }

    @Test
    void processingVersionOneCreatesVersionTwoWithoutChangingItsParent()
            throws Exception {
        source.setId(21L);
        source.setRootAudioFileId(20L);
        source.setVersionNo(1);
        source.setVersionSummary("音量优化");
        execution.setAudioFileId(21L);
        AudioFile root = source();
        root.setId(20L);
        root.setRootAudioFileId(20L);
        root.setVersionNo(0);
        root.setVersionSummary("原始版本");
        when(fileMapper.selectById(21L)).thenReturn(source);
        when(fileMapper.selectByIdForUpdate(20L)).thenReturn(root);
        when(fileMapper.selectNextVersionNo(20L)).thenReturn(2);

        executor.execute(EXECUTION_ID);

        ArgumentCaptor<AudioFile> captor =
                ArgumentCaptor.forClass(AudioFile.class);
        verify(fileMapper).insert(captor.capture());
        AudioFile versionTwo = captor.getValue();
        assertEquals(21L, versionTwo.getSourceFileId());
        assertEquals(20L, versionTwo.getRootAudioFileId());
        assertEquals(2, versionTwo.getVersionNo());
        assertEquals("音量优化", source.getVersionSummary());
        assertEquals(1, source.getVersionNo());
    }

    @Test
    void repeatedExecutionCompletionDoesNotCreateAnotherVersion()
            throws Exception {
        when(executionMapper.claim(eq(EXECUTION_ID), any()))
                .thenReturn(1, 0);

        executor.execute(EXECUTION_ID);
        executor.execute(EXECUTION_ID);

        verify(fileMapper, times(1)).insert(any(AudioFile.class));
        verify(executionMapper, times(1)).complete(
                eq(EXECUTION_ID), any(), any());
    }

    @Test
    void databaseCommitFailureDeletesUploadedObjectAndCleansWorkFiles() {
        when(executionMapper.complete(eq(EXECUTION_ID), any(), any()))
                .thenReturn(0);

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.execute(EXECUTION_ID));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_FAILED.name(),
                error.getFailureCode());
        assertTrue(error.isRetryable());
        verify(storageService).delete("repair/7/90/result.wav");
        verify(stepMapper).markProcessingSuccess(eq(EXECUTION_ID), any());
        verify(workDirectories).cleanQuietly(EXECUTION_ID);
    }

    @Test
    void unreadableOutputDoesNotUploadAndCleansWorkFiles() {
        when(metadataProbe.probe(any(Path.class)))
                .thenThrow(new IllegalStateException("probe failed"));

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.execute(EXECUTION_ID));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID.name(),
                error.getFailureCode());
        assertFalse(error.isRetryable());
        verify(storageService, never()).upload(any(), any(), any(Long.class),
                any());
        verify(storageService, never()).delete(any());
        verify(workDirectories).cleanQuietly(EXECUTION_ID);
    }

    @Test
    void incompletePersistedSnapshotFailsPermanentlyBeforeFfmpeg() {
        execution.setAcceptedStepCount(2);

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.execute(EXECUTION_ID));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY
                .name(), error.getFailureCode());
        assertFalse(error.isRetryable());
        verify(pipeline, never()).execute(any(), any(), any(), any());
        verify(stepMapper, never()).markExecutableProcessing(any(), any());
        verify(workDirectories).cleanQuietly(EXECUTION_ID);
    }

    private AudioProcessingExecution execution() {
        AudioProcessingExecution value = new AudioProcessingExecution();
        value.setId(EXECUTION_ID);
        value.setUserId(7L);
        value.setTaskId(30L);
        value.setAudioFileId(20L);
        value.setConfirmationId(40L);
        value.setExecutionStatus("PROCESSING");
        value.setAcceptedStepCount(1);
        value.setExecutableStepCount(1);
        value.setSkippedStepCount(0);
        return value;
    }

    private AudioFile source() {
        AudioFile value = new AudioFile();
        value.setId(20L);
        value.setUserId(7L);
        value.setFileRole(FileRole.ORIGINAL);
        value.setRootAudioFileId(20L);
        value.setVersionNo(0);
        value.setVersionSummary("原始版本");
        value.setOriginalName("interview.wav");
        value.setExtension("wav");
        value.setMimeType("audio/wav");
        value.setBucketName("source-bucket");
        value.setObjectKey("original/key.wav");
        value.setFileStatus(FileStatus.AVAILABLE);
        value.setDeleted(0);
        return value;
    }

    private AudioProcessingExecutionStep executableStep() {
        AudioProcessingExecutionStep step =
                new AudioProcessingExecutionStep();
        step.setId(50L);
        step.setExecutionId(EXECUTION_ID);
        step.setSourceStepConfirmationId(60L);
        step.setSourceProcessingStepId(70L);
        step.setStepOrder(1);
        step.setOperationType("TRIM_SEGMENT");
        step.setExecutionStatus("PENDING");
        step.setStartMs(100L);
        step.setEndMs(900L);
        step.setEffectiveParametersJson("{}");
        return step;
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
    }
}
