package com.audioagent.transcription.client;

import com.audioagent.transcription.dto.AsrTranscriptionRequest;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;

public interface AsrClient {
    AsrTranscriptionResponse transcribe(AsrTranscriptionRequest request);
}
