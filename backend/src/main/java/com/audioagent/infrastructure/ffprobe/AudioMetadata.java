package com.audioagent.infrastructure.ffprobe;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioMetadata {

    private Long durationMs;
}
