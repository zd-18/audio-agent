package com.audioagent.analysis.probe;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioMetadata {

    private String formatName;
    private String codecName;
    private Long durationMs;
    private Long formatDurationMs;
    private Long streamDurationMs;
    private Integer sampleRate;
    private Integer channels;
    private Long bitRate;
    private Long fileSize;
}
