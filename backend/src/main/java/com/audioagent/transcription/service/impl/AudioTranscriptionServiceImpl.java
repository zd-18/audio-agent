package com.audioagent.transcription.service.impl;

import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dispatch.TranscriptionTaskDispatcher;
import com.audioagent.transcription.dto.CreateTranscriptionTaskRequest;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import com.audioagent.transcription.service.AudioTranscriptionService;
import com.audioagent.transcription.vo.TranscriptSegmentVO;
import com.audioagent.transcription.vo.TranscriptVO;
import com.audioagent.transcription.vo.TranscriptionTaskVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AudioTranscriptionServiceImpl
        implements AudioTranscriptionService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AudioTranscriptionTaskMapper taskMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final AudioTranscriptSegmentMapper segmentMapper;
    private final AudioFileMapper audioFileMapper;
    private final TranscriptionTaskDispatcher dispatcher;
    private final TranscriptionProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TranscriptionTaskVO create(
            Long userId, CreateTranscriptionTaskRequest request) {
        requireUserId(userId);
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.TRANSCRIPTION_NOT_AVAILABLE,
                    "音频转写功能当前未启用");
        }
        String language = normalizeLanguage(request.getLanguage());
        boolean diarization = request.getEnableSpeakerDiarization() == null
                ? properties.isSpeakerDiarization()
                : request.getEnableSpeakerDiarization();
        AudioFile file = audioFileMapper.selectOwnedAvailableForUpdate(
                userId, request.getAudioFileId(),
                FileStatus.AVAILABLE.getCode());
        if (file == null) {
            rejectUnavailableFile(userId, request.getAudioFileId());
        }

        AudioTranscriptionTask reusable = taskMapper.selectReusable(
                userId, request.getAudioFileId(), language, diarization);
        if (reusable != null) {
            return TranscriptionTaskVO.from(reusable,
                    file.getOriginalName());
        }

        LocalDateTime now = LocalDateTime.now();
        AudioTranscriptionTask task = new AudioTranscriptionTask();
        task.setUserId(userId);
        task.setAudioFileId(file.getId());
        task.setStatus(TranscriptionTaskStatus.PENDING);
        task.setLanguage(language);
        task.setEnableSpeakerDiarization(diarization);
        task.setProgressPercent(0);
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (taskMapper.insert(task) != 1 || task.getId() == null) {
            throw new BusinessException(ErrorCode.TRANSCRIPTION_FAILED,
                    "转写任务创建失败，请稍后重试");
        }
        dispatcher.dispatch(task.getId());
        return TranscriptionTaskVO.from(task, file.getOriginalName());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TranscriptionTaskVO> list(
            Long userId, int current, int size,
            String status, Long audioFileId) {
        requireUserId(userId);
        validatePage(current, size);
        String normalizedStatus = normalizeStatus(status);
        Page<TranscriptionTaskVO> page = new Page<>(current, size);
        var result = taskMapper.selectTaskPage(page, userId,
                normalizedStatus, audioFileId);
        return PageResult.of(result.getRecords(), result.getCurrent(),
                result.getSize(), result.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptionTaskVO get(Long userId, Long taskId) {
        requireUserId(userId);
        requireId(taskId, "转写任务ID无效");
        TranscriptionTaskVO task = taskMapper.selectOwnedTaskView(
                userId, taskId);
        if (task == null) {
            throw new BusinessException(
                    ErrorCode.TRANSCRIPTION_TASK_NOT_FOUND);
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TranscriptionTaskVO retry(Long userId, Long taskId) {
        TranscriptionTaskVO task = get(userId, taskId);
        if (!TranscriptionTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TRANSCRIPTION_ALREADY_RUNNING,
                    "只有失败的转写任务可以重试");
        }
        if (taskMapper.resetFailed(userId, taskId,
                LocalDateTime.now()) != 1) {
            throw new BusinessException(
                    ErrorCode.TRANSCRIPTION_ALREADY_RUNNING,
                    "转写任务状态已变化，请刷新后重试");
        }
        dispatcher.dispatch(taskId);
        return get(userId, taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptVO getTranscript(Long userId, Long taskId) {
        TranscriptionTaskVO task = get(userId, taskId);
        if (!TranscriptionTaskStatus.SUCCESS.name().equals(
                task.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TRANSCRIPTION_NOT_AVAILABLE,
                    "文字稿尚未生成完成");
        }
        AudioTranscript transcript = transcriptMapper.selectByTaskAndUser(
                taskId, userId);
        if (transcript == null) {
            throw new BusinessException(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
        Page<AudioTranscriptSegment> page = new Page<>(1, 100, false);
        var segmentPage = segmentMapper.selectOwnedPage(
                page, userId, transcript.getId(), null);
        var segments = segmentPage.getRecords().stream()
                .map(TranscriptSegmentVO::from)
                .toList();
        return TranscriptVO.from(transcript, task.getAudioFileName(),
                segments);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TranscriptSegmentVO> listSegments(
            Long userId, Long transcriptId, int current, int size,
            String keyword) {
        requireUserId(userId);
        requireId(transcriptId, "文字稿ID无效");
        validatePage(current, size);
        AudioTranscript transcript = transcriptMapper.selectOwned(
                userId, transcriptId);
        if (transcript == null) {
            throw new BusinessException(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim() : null;
        Page<AudioTranscriptSegment> page = new Page<>(current, size);
        var result = segmentMapper.selectOwnedPage(
                page, userId, transcriptId, normalizedKeyword);
        var records = result.getRecords().stream()
                .map(TranscriptSegmentVO::from)
                .toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(),
                result.getTotal());
    }

    private void rejectUnavailableFile(Long userId, Long fileId) {
        AudioFile file = audioFileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_NOT_FOUND);
        }
        if (!userId.equals(file.getUserId())) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_ACCESS_DENIED);
        }
        if (file.getFileStatus() != FileStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.AUDIO_FILE_NOT_AVAILABLE,
                    "音频文件当前不可用于转写");
        }
        throw new BusinessException(ErrorCode.TRANSCRIPTION_NOT_AVAILABLE,
                "音频文件当前不可用于转写");
    }

    private String normalizeLanguage(String language) {
        String value = StringUtils.hasText(language)
                ? language.trim() : properties.getLanguage();
        return value.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return TranscriptionTaskStatus.valueOf(
                    status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "不支持的转写任务状态");
        }
    }

    private void validatePage(int current, int size) {
        if (current < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "分页参数无效，size 必须在 1 到 200 之间");
        }
    }

    private void requireUserId(Long userId) {
        requireId(userId, "用户ID无效");
    }

    private void requireId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }
}
