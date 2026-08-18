package com.audioagent.auth.service;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioResourceOwnershipServiceTest {

    @Mock
    private AudioFileMapper audioFileMapper;
    @Mock
    private AudioAnalysisTaskMapper taskMapper;
    @InjectMocks
    private AudioResourceOwnershipService service;

    @Test
    void ownerCanAccessOwnFile() {
        when(audioFileMapper.selectById(20L)).thenReturn(file(20L, 1L));
        assertDoesNotThrow(() -> service.requireFileOwned(1L, 20L));
    }

    @Test
    void userCannotAccessAnotherUsersFile() {
        when(audioFileMapper.selectById(20L)).thenReturn(file(20L, 2L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireFileOwned(1L, 20L));

        assertEquals(ErrorCode.AUDIO_FILE_NOT_FOUND.getCode(),
                error.getCode());
    }

    @Test
    void ownerCanAccessTaskThroughOwnedAudioFile() {
        when(taskMapper.selectById(30L)).thenReturn(task(30L, 20L));
        when(audioFileMapper.selectById(20L)).thenReturn(file(20L, 1L));

        assertDoesNotThrow(() -> service.requireTaskOwned(1L, 30L));
    }

    @Test
    void userCannotAccessAnotherUsersTask() {
        when(taskMapper.selectById(30L)).thenReturn(task(30L, 20L));
        when(audioFileMapper.selectById(20L)).thenReturn(file(20L, 2L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireTaskOwned(1L, 30L));

        assertEquals(ErrorCode.AUDIO_TASK_NOT_FOUND.getCode(), error.getCode());
    }

    private AudioFile file(Long id, Long userId) {
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setUserId(userId);
        return file;
    }

    private AudioAnalysisTask task(Long id, Long fileId) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(id);
        task.setAudioFileId(fileId);
        return task;
    }
}
