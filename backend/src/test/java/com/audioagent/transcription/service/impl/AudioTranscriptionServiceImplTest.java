package com.audioagent.transcription.service.impl;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dispatch.TranscriptionTaskDispatcher;
import com.audioagent.transcription.dto.CreateTranscriptionTaskRequest;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.audioagent.transcription.vo.TranscriptionTaskVO;
import com.audioagent.transcription.vo.TranscriptVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioTranscriptionServiceImplTest {

    @Mock AudioTranscriptionTaskMapper taskMapper;
    @Mock AudioTranscriptMapper transcriptMapper;
    @Mock AudioTranscriptSegmentMapper segmentMapper;
    @Mock AudioFileMapper audioFileMapper;
    @Mock TranscriptionTaskDispatcher dispatcher;
    private AudioTranscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AudioTranscriptionServiceImpl(taskMapper,
                transcriptMapper, segmentMapper, audioFileMapper,
                dispatcher, new TranscriptionProperties());
    }

    @Test
    void availableDatabaseStatusCreatesTaskAndDispatchesOnlyTaskIdentifier() {
        AudioFile file = file(20L, 7L);
        when(audioFileMapper.selectOwnedAvailableForUpdate(
                7L, 20L, FileStatus.AVAILABLE.getCode()))
                .thenReturn(file);
        when(taskMapper.insert(any(AudioTranscriptionTask.class)))
                .thenAnswer(invocation -> {
            AudioTranscriptionTask task = invocation.getArgument(0);
            task.setId(90L);
            return 1;
        });

        TranscriptionTaskVO result = service.create(7L, request(20L));

        assertEquals("90", result.getTaskId());
        assertEquals("20", result.getAudioFileId());
        assertEquals("PENDING", result.getStatus());
        verify(audioFileMapper).selectOwnedAvailableForUpdate(
                7L, 20L, FileStatus.AVAILABLE.getCode());
        verify(dispatcher).dispatch(90L);
    }

    @Test
    void duplicateClickReturnsExistingRunningTask() {
        AudioFile file = file(20L, 7L);
        AudioTranscriptionTask existing = task(90L, 7L, 20L,
                TranscriptionTaskStatus.RUNNING);
        when(audioFileMapper.selectOwnedAvailableForUpdate(
                7L, 20L, FileStatus.AVAILABLE.getCode()))
                .thenReturn(file);
        when(taskMapper.selectReusable(7L, 20L, "zh", false))
                .thenReturn(existing);

        TranscriptionTaskVO result = service.create(7L, request(20L));

        assertEquals("90", result.getTaskId());
        verify(taskMapper, never()).insert(any(AudioTranscriptionTask.class));
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void rejectsCreatingTaskForAnotherUsersAudio() {
        when(audioFileMapper.selectOwnedAvailableForUpdate(
                7L, 20L, FileStatus.AVAILABLE.getCode()))
                .thenReturn(null);
        when(audioFileMapper.selectById(20L)).thenReturn(file(20L, 8L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(7L, request(20L)));

        assertEquals(ErrorCode.AUDIO_FILE_ACCESS_DENIED.getCode(),
                exception.getCode());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void anotherUserCannotReadTaskDetails() {
        when(taskMapper.selectOwnedTaskView(7L, 90L)).thenReturn(null);
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.get(7L, 90L));
        assertEquals(ErrorCode.TRANSCRIPTION_TASK_NOT_FOUND.getCode(),
                exception.getCode());
    }

    @Test
    void failedTaskCanBeResetAndRetried() {
        TranscriptionTaskVO failed = view("FAILED");
        TranscriptionTaskVO pending = view("PENDING");
        when(taskMapper.selectOwnedTaskView(7L, 90L))
                .thenReturn(failed, pending);
        when(taskMapper.resetFailed(any(), any(), any())).thenReturn(1);

        TranscriptionTaskVO result = service.retry(7L, 90L);

        assertEquals("PENDING", result.getStatus());
        verify(dispatcher).dispatch(90L);
    }

    @Test
    void anotherUserCannotReadTranscriptSegments() {
        when(transcriptMapper.selectOwned(7L, 8001L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listSegments(7L, 8001L, 1, 200, null));

        assertEquals(ErrorCode.TRANSCRIPT_NOT_FOUND.getCode(),
                exception.getCode());
    }

    @Test
    void transcriptAndSegmentIdsRemainStringsBeyondJavascriptSafeInteger() {
        long transcriptId = 9_007_199_254_740_993L;
        long segmentId = 9_007_199_254_740_995L;
        when(taskMapper.selectOwnedTaskView(7L, 90L))
                .thenReturn(view("SUCCESS"));
        AudioTranscript transcript = new AudioTranscript();
        transcript.setId(transcriptId);
        transcript.setAudioFileId(9_007_199_254_740_997L);
        transcript.setLanguage("zh");
        transcript.setFullText("真实转写文本");
        transcript.setSegmentCount(1);
        when(transcriptMapper.selectByTaskAndUser(90L, 7L))
                .thenReturn(transcript);
        AudioTranscriptSegment segment = new AudioTranscriptSegment();
        segment.setId(segmentId);
        segment.setSegmentOrder(1);
        segment.setStartMs(0L);
        segment.setEndMs(1000L);
        segment.setText("真实转写文本");
        Page<AudioTranscriptSegment> page = new Page<>(1, 100, false);
        page.setRecords(List.of(segment));
        when(segmentMapper.selectOwnedPage(any(), eq(7L),
                eq(transcriptId), eq(null))).thenReturn(page);

        TranscriptVO result = service.getTranscript(7L, 90L);

        assertEquals(Long.toString(transcriptId), result.getTranscriptId());
        assertEquals("9007199254740997", result.getAudioFileId());
        assertEquals(Long.toString(segmentId),
                result.getSegments().getFirst().getSegmentId());
        assertEquals(1,
                result.getSegments().getFirst().getSegmentOrder());
        assertEquals(result.getSegments().getFirst().getOrder(),
                result.getSegments().getFirst().getSegmentOrder());
    }

    private static CreateTranscriptionTaskRequest request(Long fileId) {
        CreateTranscriptionTaskRequest request =
                new CreateTranscriptionTaskRequest();
        request.setAudioFileId(fileId);
        request.setLanguage("zh");
        request.setEnableSpeakerDiarization(false);
        return request;
    }

    private static AudioFile file(Long id, Long userId) {
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setUserId(userId);
        file.setOriginalName("meeting.wav");
        file.setFileStatus(FileStatus.AVAILABLE);
        file.setDeleted(0);
        return file;
    }

    private static AudioTranscriptionTask task(
            Long id, Long userId, Long fileId,
            TranscriptionTaskStatus status) {
        AudioTranscriptionTask task = new AudioTranscriptionTask();
        task.setId(id);
        task.setUserId(userId);
        task.setAudioFileId(fileId);
        task.setStatus(status);
        task.setLanguage("zh");
        task.setEnableSpeakerDiarization(false);
        task.setProgressPercent(10);
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }

    private static TranscriptionTaskVO view(String status) {
        return TranscriptionTaskVO.builder()
                .taskId("90")
                .audioFileId("20")
                .audioFileName("meeting.wav")
                .status(status)
                .language("zh")
                .progressPercent(0)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
