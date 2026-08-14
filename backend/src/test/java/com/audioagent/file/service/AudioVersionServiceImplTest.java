package com.audioagent.file.service;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.impl.AudioVersionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioVersionServiceImplTest {

    private AudioFileMapper fileMapper;
    private AudioAnalysisTaskMapper taskMapper;
    private AudioVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(AudioFileMapper.class);
        taskMapper = mock(AudioAnalysisTaskMapper.class);
        service = new AudioVersionServiceImpl(fileMapper, taskMapper);
    }

    @Test
    void queryReturnsCompleteVersionChainInVersionAndTimeOrder() {
        AudioFile root = version(10L, null, 10L, 0,
                "原始版本", 1);
        AudioFile versionOne = version(11L, 10L, 10L, 1,
                "音量优化", 2);
        AudioFile versionTwo = version(12L, 11L, 10L, 2,
                "裁剪片段", 3);
        when(fileMapper.selectById(12L)).thenReturn(versionTwo);
        when(fileMapper.selectById(10L)).thenReturn(root);
        when(fileMapper.selectVersionChain(7L, 10L))
                .thenReturn(List.of(versionTwo, root, versionOne));

        var result = service.getByAudioFile(7L, 12L);

        assertEquals(List.of(0, 1, 2), result.getVersions().stream()
                .map(version -> version.getVersionNo()).toList());
        assertEquals(List.of(10L, 11L, 12L), result.getVersions().stream()
                .map(version -> version.getAudioFileId()).toList());
        assertEquals(11L,
                result.getVersions().get(2).getParentAudioFileId());
    }

    @Test
    void taskQueryUsesTheTaskSelectedAudioVersion() {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(31L);
        task.setAudioFileId(11L);
        AudioFile root = version(10L, null, 10L, 0,
                "原始版本", 1);
        AudioFile selected = version(11L, 10L, 10L, 1,
                "音量优化", 2);
        when(taskMapper.selectById(31L)).thenReturn(task);
        when(fileMapper.selectById(11L)).thenReturn(selected);
        when(fileMapper.selectById(10L)).thenReturn(root);
        when(fileMapper.selectVersionChain(7L, 10L))
                .thenReturn(List.of(root, selected));

        var result = service.getByTask(7L, 31L);

        assertEquals(2, result.getVersions().size());
        assertEquals(11L, result.getVersions().get(1).getAudioFileId());
    }

    @Test
    void otherUsersCannotReadAVersionChain() {
        AudioFile otherUsersFile = version(10L, null, 10L, 0,
                "原始版本", 1);
        when(fileMapper.selectById(10L)).thenReturn(otherUsersFile);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getByAudioFile(8L, 10L));

        assertEquals(ErrorCode.AUDIO_FILE_ACCESS_DENIED.getCode(),
                error.getCode());
        verify(fileMapper, never()).selectVersionChain(8L, 10L);
    }

    private AudioFile version(Long id, Long parentId, Long rootId,
                              int versionNo, String summary, int minute) {
        AudioFile file = new AudioFile();
        file.setId(id);
        file.setUserId(7L);
        file.setSourceFileId(parentId);
        file.setRootAudioFileId(rootId);
        file.setVersionNo(versionNo);
        file.setVersionSummary(summary);
        file.setOriginalName("meeting-v" + versionNo + ".wav");
        file.setCreatedAt(LocalDateTime.of(2026, 8, 13, 10, minute));
        file.setDeleted(0);
        return file;
    }
}
