package com.audioagent.contentanalysis.service;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisResult;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.executor.ContentAnalysisExecution;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisResultMapper;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.service.impl.ContentAnalysisResultPersistenceServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentAnalysisResultPersistenceServiceImplTest {

    @Test
    void localJsonSerializationFailureIsNotRetryable() throws Exception {
        AudioContentAnalysisResultMapper resultMapper =
                mock(AudioContentAnalysisResultMapper.class);
        AudioContentAnalysisTaskMapper taskMapper =
                mock(AudioContentAnalysisTaskMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException(
                        "test serialization failure") {
                });
        ContentAnalysisResultPersistenceServiceImpl service =
                new ContentAnalysisResultPersistenceServiceImpl(
                        resultMapper, taskMapper, objectMapper);
        AudioContentAnalysisTask task =
                new AudioContentAnalysisTask();
        task.setId(91L);
        task.setUserId(7L);
        task.setTranscriptId(81L);
        ContentAnalysisOutput output =
                new ContentAnalysisOutput(
                        new ContentAnalysisOutput.Summary(
                                "summary", "details", List.of()),
                        List.of(), List.of(), List.of());
        ContentAnalysisExecution execution =
                new ContentAnalysisExecution(
                        output, "deepseek-test", 0, 0, 0, 1);

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> service.saveSuccess(task, execution));

        assertEquals(ErrorCode.AI_RESULT_PERSISTENCE_FAILED,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
        verify(resultMapper, never())
                .upsert(any(AudioContentAnalysisResult.class));
        verify(taskMapper, never())
                .complete(any(), any(), any());
    }
}
