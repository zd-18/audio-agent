package com.audioagent.auth.service;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudioResourceOwnershipService {

    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisTaskMapper taskMapper;

    @Transactional(readOnly = true)
    public void requireFileOwned(Long userId, Long fileId) {
        AudioFile file = audioFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_NOT_FOUND);
        }
        if (!userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }
    }

    @Transactional(readOnly = true)
    public void requireTaskOwned(Long userId, Long taskId) {
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND);
        }
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        if (file == null || !userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND);
        }
    }
}
