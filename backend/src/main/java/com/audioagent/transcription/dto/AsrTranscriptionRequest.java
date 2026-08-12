package com.audioagent.transcription.dto;

import java.nio.file.Path;

public record AsrTranscriptionRequest(
        Path file,
        String language,
        boolean enableSpeakerDiarization
) {
}
