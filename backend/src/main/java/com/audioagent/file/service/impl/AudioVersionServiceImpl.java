package com.audioagent.file.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.file.service.AudioVersionService;
import com.audioagent.file.vo.AudioVersionChainVO;
import com.audioagent.file.vo.AudioVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AudioVersionServiceImpl implements AudioVersionService {

    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisTaskMapper taskMapper;

    @Override
    @Transactional(readOnly = true)
    public AudioVersionChainVO getByAudioFile(Long userId, Long audioFileId) {
        requirePositive(userId, "用户信息无效");
        requirePositive(audioFileId, "音频版本无效");
        return chain(userId, requireOwned(userId, audioFileId));
    }

    @Override
    @Transactional(readOnly = true)
    public AudioVersionChainVO getByTask(Long userId, Long taskId) {
        requirePositive(userId, "用户信息无效");
        requirePositive(taskId, "分析任务无效");
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "分析任务不存在");
        }
        return chain(userId, requireOwned(userId, task.getAudioFileId()));
    }

    private AudioVersionChainVO chain(Long userId, AudioFile selected) {
        Long rootId = selected.getRootAudioFileId() == null
                ? selected.getId() : selected.getRootAudioFileId();
        AudioFile root = requireOwned(userId, rootId);
        List<AudioFile> files = audioFileMapper.selectVersionChain(
                userId, root.getId());
        List<AudioVersionVO> versions = (files == null ? List.<AudioFile>of()
                : files).stream()
                .sorted(Comparator.comparing(
                                AudioFile::getVersionNo,
                                Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(AudioFile::getCreatedAt,
                                Comparator.nullsFirst(
                                        java.time.LocalDateTime::compareTo))
                        .thenComparing(AudioFile::getId))
                .map(AudioVersionVO::from)
                .toList();
        return AudioVersionChainVO.builder().versions(versions).build();
    }

    private AudioFile requireOwned(Long userId, Long audioFileId) {
        AudioFile file = audioFileMapper.selectById(audioFileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_NOT_FOUND,
                    "音频版本不存在");
        }
        if (!userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED,
                    "无权访问该音频版本");
        }
        return file;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }
}
