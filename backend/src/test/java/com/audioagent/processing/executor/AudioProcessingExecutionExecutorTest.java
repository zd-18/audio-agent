package com.audioagent.processing.executor;

import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.AudioVersionSummaryBuilder;
import com.audioagent.infrastructure.minio.MinioProperties;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.exception.ProcessingExecutionErrorClassifier;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mapper.AudioProcessingExecutionStepMapper;
import com.audioagent.processing.pipeline.AudioProcessingPipeline;
import com.audioagent.processing.pipeline.ProcessingOutputValidator;
import com.audioagent.processing.critic.ProcessingResultCritic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingExecutionExecutorTest {

    private AudioProcessingExecutionMapper executionMapper;
    private AudioFileMapper fileMapper;
    private AudioProcessingPipeline pipeline;
    private ProcessingExecutionWorkDirectory workDirectories;
    private AudioProcessingExecutionExecutor executor;

    @BeforeEach
    void setUp() {
        executionMapper = mock(AudioProcessingExecutionMapper.class);
        AudioProcessingExecutionStepMapper stepMapper = mock(
                AudioProcessingExecutionStepMapper.class);
        fileMapper = mock(AudioFileMapper.class);
        pipeline = mock(AudioProcessingPipeline.class);
        workDirectories = mock(ProcessingExecutionWorkDirectory.class);
        executor = new AudioProcessingExecutionExecutor(executionMapper,
                stepMapper, fileMapper, new AudioVersionSummaryBuilder(new ObjectMapper()),
                mock(MinioStorageService.class),
                new MinioProperties(), pipeline,
                mock(AudioMetadataProbe.class),
                mock(ProcessingOutputValidator.class),
                mock(ProcessingResultCritic.class), workDirectories,
                new ProcessingExecutionErrorClassifier(),
                new ObjectMapper(), mock(TransactionTemplate.class));
    }

    @Test
    void duplicateMessageDoesNotExecutePipeline() {
        when(executionMapper.claim(eq(90L), any())).thenReturn(0);
        AudioProcessingExecution existing = new AudioProcessingExecution();
        existing.setId(90L);
        existing.setExecutionStatus("SUCCESS");
        when(executionMapper.selectExecutionById(90L))
                .thenReturn(existing);

        executor.execute(90L);

        verify(pipeline, never()).execute(any(), any(), any(), any());
        verify(workDirectories, never()).prepare(any());
    }

    @Test
    void missingSourceFileIsPermanentFailure() {
        when(executionMapper.claim(eq(90L), any())).thenReturn(1);
        AudioProcessingExecution execution = new AudioProcessingExecution();
        execution.setId(90L);
        execution.setAudioFileId(20L);
        execution.setUserId(7L);
        execution.setExecutionStatus("PROCESSING");
        when(executionMapper.selectExecutionById(90L))
                .thenReturn(execution);
        when(fileMapper.selectById(20L)).thenReturn(null);

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND
                .name(), error.getFailureCode());
        assertFalse(error.isRetryable());
        verify(workDirectories).cleanQuietly(90L);
    }
}
