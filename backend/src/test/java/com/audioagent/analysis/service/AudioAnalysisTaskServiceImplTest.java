package com.audioagent.analysis.service;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.dto.CreateTaskRequest;
import com.audioagent.analysis.event.AudioAnalysisTaskCreatedEvent;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.enums.AnalysisType;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.service.impl.AudioAnalysisTaskServiceImpl;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioAnalysisTaskServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock
    private AudioAnalysisTaskMapper taskMapper;
    @Mock
    private AudioAnalysisResultMapper resultMapper;
    @Mock
    private AudioFileMapper audioFileMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LoudnessEvaluator loudnessEvaluator;
    @Mock
    private AudioResourceOwnershipService ownershipService;
    @InjectMocks
    private AudioAnalysisTaskServiceImpl service;

    @Test
    void listTasksUsesDefaultPageAndJoinResultWithoutNPlusOne() {
        TaskListVO task = task(31L, 11L, "meeting.wav");
        mockPage(List.of(task), 1);

        PageResult<TaskListVO> result = service.listTasks(
                USER_ID, 1, 10, null, null, null, null);

        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(1, result.getTotal());
        assertEquals("meeting.wav", result.getRecords().getFirst().getFileName());

        ArgumentCaptor<Page<TaskListVO>> captor = ArgumentCaptor.forClass(Page.class);
        verify(taskMapper, times(1)).selectTaskPage(
                captor.capture(), eq(USER_ID), isNull(), isNull(), isNull(),
                isNull());
        assertEquals(1, captor.getValue().getCurrent());
        assertEquals(10, captor.getValue().getSize());
        verifyNoInteractions(audioFileMapper, resultMapper, eventPublisher);
    }

    @Test
    void listTasksFiltersByEverySupportedStatus() {
        for (String status : List.of("FAILED", "SUCCESS", "PROCESSING", "PENDING")) {
            mockPage(List.of(), 0);
            service.listTasks(USER_ID, 1, 10, status.toLowerCase(), null, null,
                    null);
            verify(taskMapper).selectTaskPage(any(Page.class), eq(USER_ID),
                    eq(status), isNull(), isNull(), isNull());
            reset(taskMapper);
        }
    }

    @Test
    void listTasksFiltersByAudioFileId() {
        mockPage(List.of(), 0);

        service.listTasks(USER_ID, 1, 10, null, 99L, null, null);

        verify(taskMapper).selectTaskPage(any(Page.class), eq(USER_ID),
                isNull(), eq(99L), isNull(), isNull());
    }

    @Test
    void listTasksFiltersByAnalysisType() {
        mockPage(List.of(), 0);

        service.listTasks(USER_ID, 1, 10, null, null, "full", null);

        verify(taskMapper).selectTaskPage(any(Page.class), eq(USER_ID),
                isNull(), isNull(), eq("FULL"), isNull());
    }

    @Test
    void listTasksTrimsFileNameKeyword() {
        mockPage(List.of(), 0);

        service.listTasks(USER_ID, 1, 10, null, null, null,
                "  powerful  ");

        verify(taskMapper).selectTaskPage(any(Page.class), eq(USER_ID),
                isNull(), isNull(), isNull(), eq("powerful"));
    }

    @Test
    void listTasksIgnoresBlankFileNameKeyword() {
        mockPage(List.of(), 0);

        service.listTasks(USER_ID, 1, 10, null, null, null, "   ");

        verify(taskMapper).selectTaskPage(any(Page.class), eq(USER_ID),
                isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void listTasksReturnsEmptyRecords() {
        mockPage(List.of(), 0);

        PageResult<TaskListVO> result = service.listTasks(
                USER_ID, 1, 10, "SUCCESS", 999L, "FULL", "missing");

        assertNotNull(result.getRecords());
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
    }

    @Test
    void legacyTaskWithoutLoudnessStillReturnsDetail() {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(31L);
        task.setAudioFileId(11L);
        task.setAnalysisType(AnalysisType.FULL);
        task.setStatus(AnalysisTaskStatus.SUCCESS);
        AudioAnalysisResult storedResult = new AudioAnalysisResult();
        storedResult.setTaskId(31L);
        storedResult.setFormatName("wav");
        when(taskMapper.selectById(31L)).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(storedResult);

        TaskVO detail = service.getTaskDetail(USER_ID, 31L);

        assertNotNull(detail.getResult());
        assertEquals("wav", detail.getResult().getFormatName());
        assertNull(detail.getResult().getLoudness());
        verifyNoInteractions(loudnessEvaluator);
    }

    @Test
    void duplicateUploadedEventDoesNotCreateOrDispatchAnotherTask() {
        AudioFile file = new AudioFile();
        file.setId(11L);
        file.setUserId(USER_ID);
        file.setDeleted(0);
        AudioAnalysisTask created = new AudioAnalysisTask();
        created.setId(31L);
        created.setAudioFileId(11L);
        created.setSourceEventId(501L);
        created.setAnalysisType(AnalysisType.FULL);
        created.setStatus(AnalysisTaskStatus.PENDING);
        when(taskMapper.selectBySourceEventId(501L))
                .thenReturn(null, created);
        when(audioFileMapper.selectById(11L)).thenReturn(file);
        when(taskMapper.insert(any(AudioAnalysisTask.class)))
                .thenAnswer(invocation -> {
                    AudioAnalysisTask task = invocation.getArgument(0);
                    task.setId(31L);
                    return 1;
                });

        TaskVO first = service.createTaskFromUploadedFile(
                11L, USER_ID, 501L);
        TaskVO duplicate = service.createTaskFromUploadedFile(
                11L, USER_ID, 501L);

        assertEquals(first.getTaskId(), duplicate.getTaskId());
        verify(taskMapper, times(1)).insert(any(AudioAnalysisTask.class));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    void failedTaskCanOnlyReturnToPendingThroughManualRetry() {
        AudioAnalysisTask failed = new AudioAnalysisTask();
        failed.setId(31L);
        failed.setAudioFileId(11L);
        failed.setAnalysisType(AnalysisType.FULL);
        failed.setStatus(AnalysisTaskStatus.FAILED);
        AudioAnalysisTask pending = new AudioAnalysisTask();
        pending.setId(31L);
        pending.setAudioFileId(11L);
        pending.setAnalysisType(AnalysisType.FULL);
        pending.setStatus(AnalysisTaskStatus.PENDING);
        when(taskMapper.selectById(31L)).thenReturn(failed, pending);
        when(taskMapper.update(any(), any())).thenReturn(1);

        TaskVO retried = service.retryTask(USER_ID, 31L);

        assertEquals("PENDING", retried.getStatus());
        verify(taskMapper).update(any(), any());
        verify(eventPublisher).publishEvent(
                any(AudioAnalysisTaskCreatedEvent.class));
    }

    @Test
    void createTaskKeepsTheExplicitlySelectedAudioVersionAsInput() {
        long selectedVersionId = 922337203685477500L;
        AudioFile selectedVersion = new AudioFile();
        selectedVersion.setId(selectedVersionId);
        selectedVersion.setUserId(USER_ID);
        selectedVersion.setSourceFileId(11L);
        selectedVersion.setRootAudioFileId(11L);
        selectedVersion.setVersionNo(1);
        selectedVersion.setDeleted(0);
        when(audioFileMapper.selectById(selectedVersionId))
                .thenReturn(selectedVersion);
        when(taskMapper.insert(any(AudioAnalysisTask.class)))
                .thenAnswer(invocation -> {
                    AudioAnalysisTask task = invocation.getArgument(0);
                    task.setId(32L);
                    return 1;
                });
        CreateTaskRequest request = new CreateTaskRequest();
        request.setAudioFileId(selectedVersionId);
        request.setAnalysisType(AnalysisType.FULL.name());

        TaskVO created = service.createTask(USER_ID, request);

        assertEquals(selectedVersionId, created.getAudioFileId());
        ArgumentCaptor<AudioAnalysisTask> taskCaptor =
                ArgumentCaptor.forClass(AudioAnalysisTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals(selectedVersionId,
                taskCaptor.getValue().getAudioFileId());
        ArgumentCaptor<AudioAnalysisTaskCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(AudioAnalysisTaskCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(selectedVersionId,
                eventCaptor.getValue().getAudioFileId());
    }

    @Test
    void foreignAudioCannotCreateTaskInsideServiceBoundary() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setAudioFileId(20L);
        doThrow(new BusinessException(ErrorCode.AUDIO_FILE_NOT_FOUND,
                "资源不存在或不可访问"))
                .when(ownershipService).requireFileOwned(USER_ID, 20L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createTask(USER_ID, request));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_FOUND.getCode(),
                error.getCode());
        verify(taskMapper, never()).insert(any(AudioAnalysisTask.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void foreignTaskCannotReadDetailOrRetryInsideServiceBoundary() {
        doThrow(new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                "资源不存在或不可访问"))
                .when(ownershipService).requireTaskOwned(USER_ID, 31L);

        BusinessException detail = assertThrows(BusinessException.class,
                () -> service.getTaskDetail(USER_ID, 31L));
        BusinessException retry = assertThrows(BusinessException.class,
                () -> service.retryTask(USER_ID, 31L));

        assertEquals(ErrorCode.AUDIO_TASK_NOT_FOUND.getCode(),
                detail.getCode());
        assertEquals(detail.getMessage(), retry.getMessage());
        verify(taskMapper, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockPage(List<TaskListVO> records, long total) {
        when(taskMapper.selectTaskPage(any(Page.class), any(), any(), any(),
                any(), any()))
                .thenAnswer(invocation -> {
                    Page<TaskListVO> page = invocation.getArgument(0);
                    page.setRecords(records);
                    page.setTotal(total);
                    return page;
                });
    }

    private static TaskListVO task(Long taskId, Long audioFileId,
                                   String fileName) {
        TaskListVO task = new TaskListVO();
        task.setTaskId(taskId);
        task.setAudioFileId(audioFileId);
        task.setFileName(fileName);
        task.setAnalysisType("FULL");
        task.setStatus("SUCCESS");
        task.setProgress(100);
        return task;
    }
}
