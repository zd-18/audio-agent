package com.audioagent.transcription.service.impl;

import com.audioagent.common.enums.ErrorCode;
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
import com.audioagent.transcription.service.TranscriptionResultPersistenceService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class TranscriptionResultPersistenceServiceImpl
        implements TranscriptionResultPersistenceService {

    private final AudioTranscriptMapper transcriptMapper;
    private final AudioTranscriptSegmentMapper segmentMapper;
    private final AudioTranscriptionTaskMapper taskMapper;
    private final TranscriptionProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(AudioTranscriptionTask task,
                     AsrTranscriptionResponse response) {
        AudioTranscript existing = transcriptMapper.selectByTaskAndUser(
                task.getId(), task.getUserId());
        if (existing != null) {
            if (taskMapper.complete(task.getId(), properties.getProvider(),
                    properties.getModelName(), LocalDateTime.now()) != 1) {
                AudioTranscriptionTask current =
                        taskMapper.selectById(task.getId());
                if (current == null
                        || current.getStatus()
                        != TranscriptionTaskStatus.SUCCESS) {
                    throw failed("转写任务状态无法完成");
                }
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        AudioTranscript transcript = new AudioTranscript();
        transcript.setId(IdWorker.getId());
        transcript.setUserId(task.getUserId());
        transcript.setAudioFileId(task.getAudioFileId());
        transcript.setTranscriptionTaskId(task.getId());
        transcript.setLanguage(response.getLanguage().trim());
        transcript.setFullText(response.getFullText().trim());
        transcript.setDurationMs(response.getDurationMs());
        transcript.setSpeakerCount(response.getSpeakerCount());
        transcript.setSegmentCount(response.getSegments().size());
        transcript.setCreatedAt(now);
        transcript.setUpdatedAt(now);
        if (transcriptMapper.insert(transcript) != 1) {
            throw failed("文字稿保存失败");
        }

        for (AsrSegmentResponse source : response.getSegments()) {
            AudioTranscriptSegment segment = new AudioTranscriptSegment();
            segment.setId(IdWorker.getId());
            segment.setUserId(task.getUserId());
            segment.setTranscriptId(transcript.getId());
            segment.setSegmentOrder(source.getOrder());
            segment.setStartMs(source.getStartMs());
            segment.setEndMs(source.getEndMs());
            segment.setSpeakerLabel(cleanSpeaker(source.getSpeaker()));
            segment.setText(source.getText().trim());
            segment.setConfidence(source.getConfidence() == null ? null
                    : source.getConfidence().setScale(5,
                    RoundingMode.HALF_UP));
            segment.setCreatedAt(now);
            if (segmentMapper.insert(segment) != 1) {
                throw failed("文字稿片段保存失败");
            }
        }

        if (taskMapper.complete(task.getId(), properties.getProvider(),
                properties.getModelName(), now) != 1) {
            throw failed("转写任务状态无法完成");
        }
        log.info("Transcription result persisted, taskId={}, "
                        + "stage=PERSIST_RESULT, fullTextLength={}, "
                        + "segmentCount={}, durationMs={}",
                task.getId(), transcript.getFullText().length(),
                transcript.getSegmentCount(), transcript.getDurationMs());
    }

    private String cleanSpeaker(String speaker) {
        return speaker == null || speaker.isBlank() ? null : speaker.trim();
    }

    private TranscriptionException failed(String message) {
        return new TranscriptionException(
                ErrorCode.TRANSCRIPT_PERSISTENCE_FAILED, false, message);
    }
}
