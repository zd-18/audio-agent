package com.audioagent.transcription.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.transcription.client.AsrClient;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.exception.TranscriptionErrorClassifier;
import com.audioagent.transcription.exception.TranscriptionException;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.audioagent.transcription.service.TranscriptionResultPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLSyntaxErrorException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioTranscriptionTaskExecutorTest {

    @TempDir Path tempDirectory;
    @Mock AudioTranscriptionTaskMapper taskMapper;
    @Mock AudioFileMapper audioFileMapper;
    @Mock MinioStorageService storageService;
    @Mock TranscriptionWorkDirectory workDirectories;
    @Mock AudioStandardizer standardizer;
    @Mock AsrClient asrClient;
    @Mock AsrResponseValidator responseValidator;
    @Mock TranscriptionResultPersistenceService persistenceService;
    private AudioTranscriptionTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AudioTranscriptionTaskExecutor(taskMapper,
                audioFileMapper, storageService, workDirectories,
                standardizer, asrClient, responseValidator,
                persistenceService, new TranscriptionErrorClassifier());
    }

    @Test
    void invokesAsrAndPersistsValidatedResponse() throws Exception {
        Path standardized = tempDirectory.resolve("standardized.wav");
        Files.write(standardized, new byte[100]);
        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setSegments(List.of(new AsrSegmentResponse()));
        stubPipeline(standardized);
        when(asrClient.transcribe(any())).thenReturn(response);

        executor.execute(90L);

        verify(responseValidator).validate(response);
        verify(persistenceService).save(any(), eq(response));
        var progressOrder = inOrder(taskMapper, asrClient,
                responseValidator, persistenceService);
        progressOrder.verify(taskMapper)
                .updateProgress(eq(90L), eq(25), any());
        progressOrder.verify(taskMapper)
                .updateProgress(eq(90L), eq(45), any());
        progressOrder.verify(asrClient).transcribe(any());
        progressOrder.verify(responseValidator).validate(response);
        progressOrder.verify(taskMapper)
                .updateProgress(eq(90L), eq(85), any());
        progressOrder.verify(persistenceService).save(any(), eq(response));
        verify(workDirectories).cleanQuietly(90L);
    }

    @Test
    void asrUnavailableStillCleansTemporaryFiles() throws Exception {
        Path standardized = tempDirectory.resolve("standardized.wav");
        Files.write(standardized, new byte[100]);
        stubPipeline(standardized);
        when(asrClient.transcribe(any())).thenThrow(
                new TranscriptionException(
                        ErrorCode.ASR_SERVICE_UNAVAILABLE, true,
                        "语音识别服务暂时不可用"));

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.ASR_SERVICE_UNAVAILABLE,
                exception.getErrorCode());
        verify(workDirectories).cleanQuietly(90L);
    }

    @Test
    void schemaFailureAfterAsrIsPermanent() throws Exception {
        Path standardized = tempDirectory.resolve("standardized.wav");
        Files.write(standardized, new byte[100]);
        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setFullText("test transcript");
        response.setDurationMs(1000L);
        response.setSegments(List.of(new AsrSegmentResponse()));
        stubPipeline(standardized);
        when(asrClient.transcribe(any())).thenReturn(response);
        doThrow(new BadSqlGrammarException(
                "insert segment",
                "INSERT INTO audio_transcript_segment(user_id) VALUES (?)",
                new SQLSyntaxErrorException("Unknown column 'user_id'")))
                .when(persistenceService).save(any(), eq(response));

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED,
                exception.getErrorCode());
        assertFalse(exception.isRetryable());
        verify(asrClient).transcribe(any());
        verify(workDirectories).cleanQuietly(90L);
    }

    @Test
    void standardizedFileNullDoesNotSendHttpRequest() throws Exception {
        stubPipeline(null);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
        verify(asrClient, never()).transcribe(any());
        verify(workDirectories).cleanQuietly(90L);
    }

    @Test
    void standardizedFileSizeZeroFailsWithoutHttpRequest() throws Exception {
        Path standardized = tempDirectory.resolve("standardized.wav");
        Files.write(standardized, new byte[0]);
        stubPipeline(standardized);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
        verify(asrClient, never()).transcribe(any());
        verify(workDirectories).cleanQuietly(90L);
    }

    @Test
    void standardizedFileDoesNotExistFailsWithoutHttpRequest()
            throws Exception {
        Path standardized = tempDirectory.resolve("nonexistent.wav");
        stubPipeline(standardized);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> executor.execute(90L));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
        verify(asrClient, never()).transcribe(any());
        verify(workDirectories).cleanQuietly(90L);
    }

    private void stubPipeline(Path standardized) throws Exception {
        AudioTranscriptionTask task = new AudioTranscriptionTask();
        task.setId(90L);
        task.setUserId(7L);
        task.setAudioFileId(20L);
        task.setLanguage("zh");
        task.setEnableSpeakerDiarization(false);
        task.setStatus(TranscriptionTaskStatus.RUNNING);
        AudioFile file = new AudioFile();
        file.setId(20L);
        file.setUserId(7L);
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setDeleted(0);
        file.setExtension("wav");
        file.setBucketName("audio-agent");
        file.setObjectKey("original/source.wav");
        when(taskMapper.selectById(90L)).thenReturn(task);
        when(audioFileMapper.selectById(20L)).thenReturn(file);
        when(workDirectories.prepare(90L)).thenReturn(tempDirectory);
        when(storageService.getObject("audio-agent",
                "original/source.wav")).thenReturn(
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(standardizer.standardize(any(), eq(tempDirectory)))
                .thenReturn(standardized);
    }
}
