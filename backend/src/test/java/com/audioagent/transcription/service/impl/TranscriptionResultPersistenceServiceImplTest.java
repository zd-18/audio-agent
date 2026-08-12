package com.audioagent.transcription.service.impl;

import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.exception.TranscriptionException;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionResultPersistenceServiceImplTest {

    @Mock AudioTranscriptMapper transcriptMapper;
    @Mock AudioTranscriptSegmentMapper segmentMapper;
    @Mock AudioTranscriptionTaskMapper taskMapper;
    private TranscriptionResultPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TranscriptionResultPersistenceServiceImpl(
                transcriptMapper, segmentMapper, taskMapper,
                new TranscriptionProperties());
    }

    @Test
    void savesFullTextSegmentsThenMarksTaskSuccess() {
        when(transcriptMapper.insert(any(AudioTranscript.class)))
                .thenReturn(1);
        when(segmentMapper.insert(any(AudioTranscriptSegment.class)))
                .thenReturn(1);
        when(taskMapper.complete(eq(90L), eq("funasr"),
                eq("paraformer-zh"), any())).thenReturn(1);

        service.save(task(), response());

        ArgumentCaptor<AudioTranscript> transcriptCaptor =
                ArgumentCaptor.forClass(AudioTranscript.class);
        verify(transcriptMapper).insert(transcriptCaptor.capture());
        assertEquals("真实模型转写文本",
                transcriptCaptor.getValue().getFullText());
        assertEquals(2, transcriptCaptor.getValue().getSegmentCount());

        ArgumentCaptor<AudioTranscriptSegment> segmentCaptor =
                ArgumentCaptor.forClass(AudioTranscriptSegment.class);
        verify(segmentMapper, times(2)).insert(segmentCaptor.capture());
        List<AudioTranscriptSegment> saved = segmentCaptor.getAllValues();
        assertEquals(List.of(1, 2), saved.stream()
                .map(AudioTranscriptSegment::getSegmentOrder).toList());
        assertEquals(7L, saved.getFirst().getUserId());
        assertEquals(0L, saved.getFirst().getStartMs());
        assertEquals(2000L, saved.getFirst().getEndMs());
        assertEquals(2300L, saved.getLast().getStartMs());
        assertEquals(4500L, saved.getLast().getEndMs());
        assertEquals(new BigDecimal("0.93457"),
                saved.getFirst().getConfidence());
        assertNull(saved.getLast().getSpeakerLabel());
        assertNull(saved.getLast().getConfidence());
        verify(taskMapper).complete(eq(90L), eq("funasr"),
                eq("paraformer-zh"), any());
    }

    @Test
    void taskIsNotSuccessfulWhenSegmentSaveFails() {
        when(transcriptMapper.insert(any(AudioTranscript.class)))
                .thenReturn(1);
        when(segmentMapper.insert(any(AudioTranscriptSegment.class)))
                .thenReturn(0);

        TranscriptionException failure = assertThrows(
                TranscriptionException.class,
                () -> service.save(task(), response()));

        assertEquals(com.audioagent.common.enums.ErrorCode
                        .TRANSCRIPT_PERSISTENCE_FAILED,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
        verify(taskMapper, never()).complete(any(), any(), any(), any());
    }

    @Test
    void duplicatePersistenceDoesNotCreateSecondTranscript() {
        when(transcriptMapper.selectByTaskAndUser(90L, 7L))
                .thenReturn(new AudioTranscript());
        when(taskMapper.complete(eq(90L), any(), any(), any()))
                .thenReturn(1);

        service.save(task(), response());

        verify(transcriptMapper, never()).insert(any(AudioTranscript.class));
        verify(segmentMapper, never())
                .insert(any(AudioTranscriptSegment.class));
    }

    private static AudioTranscriptionTask task() {
        AudioTranscriptionTask task = new AudioTranscriptionTask();
        task.setId(90L);
        task.setUserId(7L);
        task.setAudioFileId(20L);
        task.setStatus(TranscriptionTaskStatus.RUNNING);
        return task;
    }

    private static AsrTranscriptionResponse response() {
        AsrSegmentResponse first = new AsrSegmentResponse();
        first.setOrder(1);
        first.setStartMs(0L);
        first.setEndMs(2000L);
        first.setText("真实模型");
        first.setConfidence(new BigDecimal("0.934567"));
        AsrSegmentResponse second = new AsrSegmentResponse();
        second.setOrder(2);
        second.setStartMs(2300L);
        second.setEndMs(4500L);
        second.setText("转写文本");
        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setLanguage("zh");
        response.setDurationMs(5000L);
        response.setFullText("真实模型转写文本");
        response.setSegments(List.of(first, second));
        return response;
    }
}
