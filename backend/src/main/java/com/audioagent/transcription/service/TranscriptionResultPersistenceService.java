package com.audioagent.transcription.service;

import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.entity.AudioTranscriptionTask;

public interface TranscriptionResultPersistenceService {
    void save(AudioTranscriptionTask task,
              AsrTranscriptionResponse response);
}
