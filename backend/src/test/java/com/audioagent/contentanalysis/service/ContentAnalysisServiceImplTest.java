package com.audioagent.contentanalysis.service;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.dispatch.ContentAnalysisTaskDispatcher;
import com.audioagent.contentanalysis.dto.CreateContentAnalysisTaskRequest;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisResultMapper;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.AnalysisTypeCodec;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.service.impl.ContentAnalysisServiceImpl;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAnalysisServiceImplTest {

    @Mock AudioContentAnalysisTaskMapper taskMapper;
    @Mock AudioContentAnalysisResultMapper resultMapper;
    @Mock AudioTranscriptMapper transcriptMapper;
    @Mock AudioFileMapper audioFileMapper;
    @Mock ContentAnalysisTaskDispatcher dispatcher;

    private DeepSeekProperties properties;
    private ContentAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        service = new ContentAnalysisServiceImpl(
                taskMapper, resultMapper, transcriptMapper,
                audioFileMapper, dispatcher, properties,
                new AnalysisTypeCodec(objectMapper), objectMapper);
    }

    @Test
    void missingApiKeyReturnsSafeConfigurationError() {
        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create(7L, request("81")));

        assertEquals(ErrorCode.AI_SERVICE_NOT_CONFIGURED.getCode(),
                failure.getCode());
        verify(transcriptMapper, never())
                .selectOwnedForUpdate(any(), any());
    }

    @Test
    void cannotAnalyzeAnotherUsersTranscript() {
        properties.setApiKey("test-only-key");
        when(transcriptMapper.selectOwnedForUpdate(7L, 81L))
                .thenReturn(null);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create(7L, request("81")));

        assertEquals(ErrorCode.TRANSCRIPT_NOT_FOUND.getCode(),
                failure.getCode());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void emptyTranscriptCannotCreateTask() {
        properties.setApiKey("test-only-key");
        AudioTranscript transcript = transcript(81L, "   ");
        when(transcriptMapper.selectOwnedForUpdate(7L, 81L))
                .thenReturn(transcript);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.create(7L, request("81")));

        assertEquals(ErrorCode.AI_TRANSCRIPT_EMPTY.getCode(),
                failure.getCode());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void createsTaskWithStringSnowflakeIds() {
        properties.setApiKey("test-only-key");
        long transcriptId = 9_007_199_254_740_993L;
        when(transcriptMapper.selectOwnedForUpdate(
                7L, transcriptId))
                .thenReturn(transcript(transcriptId, "真实文字稿"));
        when(taskMapper.insert(any(AudioContentAnalysisTask.class)))
                .thenAnswer(invocation -> {
                    AudioContentAnalysisTask task =
                            invocation.getArgument(0);
                    task.setId(9_007_199_254_740_995L);
                    return 1;
                });

        var result = service.create(
                7L, request(Long.toString(transcriptId)));

        assertEquals("9007199254740995", result.getTaskId());
        assertEquals("9007199254740993",
                result.getTranscriptId());
        assertEquals(4, result.getAnalysisTypes().size());
        verify(dispatcher).dispatch(9_007_199_254_740_995L);
    }

    @Test
    void createWritesAnalysisTypesAsJsonString() {
        properties.setApiKey("test-only-key");
        when(transcriptMapper.selectOwnedForUpdate(7L, 81L))
                .thenReturn(transcript(81L, "真实文字稿"));
        doAnswer(invocation -> {
            AudioContentAnalysisTask task = invocation.getArgument(0);
            task.setId(92L);
            assertEquals(
                    "[\"CHAPTERS\",\"KEY_POINTS\","
                            + "\"SPEECH_ISSUES\",\"SUMMARY\"]",
                    task.getAnalysisTypesJson());
            return 1;
        }).when(taskMapper).insert(any(AudioContentAnalysisTask.class));

        service.create(7L, request("81"));

        verify(taskMapper).insert(any(AudioContentAnalysisTask.class));
    }

    @Test
    void failedTaskWithValidAnalysisTypesCanBeRead() {
        AudioContentAnalysisTask task = new AudioContentAnalysisTask();
        task.setId(91L);
        task.setUserId(7L);
        task.setTranscriptId(81L);
        task.setStatus(ContentAnalysisTaskStatus.FAILED);
        task.setAnalysisTypesJson(
                "[\"SUMMARY\",\"KEY_POINTS\"]");
        task.setProgressPercent(0);
        task.setRetryCount(0);
        task.setFailureCode(
                ErrorCode.AI_RESULT_PERSISTENCE_FAILED.name());
        when(taskMapper.selectOwned(eq(7L), eq(91L)))
                .thenReturn(task);

        var result = service.get(7L, "91");

        assertEquals("FAILED", result.getStatus());
        assertEquals(2, result.getAnalysisTypes().size());
        assertNotNull(result.getFailureCode());
    }

    @Test
    void userIsolationHidesTaskExistence() {
        properties.setApiKey("test-only-key");
        when(taskMapper.selectOwned(7L, 91L)).thenReturn(null);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.getResult(7L, "91"));

        assertEquals(ErrorCode.AI_TASK_NOT_FOUND.getCode(),
                failure.getCode());
    }

    private CreateContentAnalysisTaskRequest request(String transcriptId) {
        CreateContentAnalysisTaskRequest request =
                new CreateContentAnalysisTaskRequest();
        request.setTranscriptId(transcriptId);
        request.setAnalysisTypes(EnumSet.allOf(AnalysisType.class));
        request.setSummaryStyle(SummaryStyle.STANDARD);
        return request;
    }

    private AudioTranscript transcript(long id, String fullText) {
        AudioTranscript transcript = new AudioTranscript();
        transcript.setId(id);
        transcript.setUserId(7L);
        transcript.setAudioFileId(20L);
        transcript.setLanguage("zh");
        transcript.setFullText(fullText);
        transcript.setSegmentCount(1);
        return transcript;
    }
}
