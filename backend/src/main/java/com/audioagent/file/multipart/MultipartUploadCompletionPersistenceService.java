package com.audioagent.file.multipart;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.event.AudioFileUploadedEvent;
import com.audioagent.file.event.AudioFileUploadedEventType;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.outbox.publisher.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MultipartUploadCompletionPersistenceService {

    private final AudioFileMapper audioFileMapper;
    private final OutboxPublisher outboxPublisher;

    @Transactional(rollbackFor = Exception.class)
    public AudioFile persistCompletedUpload(AudioFile audioFile) {
        AudioFile existing = audioFileMapper.selectByStorageObject(
                audioFile.getBucketName(), audioFile.getObjectKey());
        AudioFile persisted = existing == null
                ? insertAudioFile(audioFile)
                : requireSameUpload(existing, audioFile);
        ensureUploadedEvent(persisted);
        return persisted;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureUploadedEvent(AudioFile audioFile) {
        outboxPublisher.publish(
                AudioFileUploadedEventType.AGGREGATE_TYPE,
                audioFile.getId().toString(),
                AudioFileUploadedEventType.AUDIO_FILE_UPLOADED,
                new AudioFileUploadedEvent(
                        audioFile.getId(),
                        audioFile.getUserId(),
                        AudioFileUploadedEvent.CURRENT_VERSION));
    }

    private AudioFile insertAudioFile(AudioFile audioFile) {
        if (audioFileMapper.insert(audioFile) != 1
                || audioFile.getId() == null) {
            throw new BusinessException(
                    ErrorCode.AUDIO_FILE_UPLOAD_FAILED,
                    "Failed to persist uploaded audio file metadata");
        }
        return audioFile;
    }

    private AudioFile requireSameUpload(AudioFile existing,
                                        AudioFile candidate) {
        if (!Objects.equals(existing.getUserId(), candidate.getUserId())
                || !Objects.equals(existing.getSha256(),
                candidate.getSha256())) {
            throw new BusinessException(
                    ErrorCode.MULTIPART_UPLOAD_STATE_INVALID,
                    "The completed storage object belongs to another upload");
        }
        return existing;
    }
}
