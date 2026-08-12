package com.audioagent.analysis.service;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.enums.AnalysisType;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.service.impl.AudioAnalysisTaskServiceImpl;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.common.api.PageResult;
import com.audioagent.file.mapper.AudioFileMapper;
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

        TaskVO detail = service.getTaskDetail(31L);

        assertNotNull(detail.getResult());
        assertEquals("wav", detail.getResult().getFormatName());
        assertNull(detail.getResult().getLoudness());
        verifyNoInteractions(loudnessEvaluator);
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
