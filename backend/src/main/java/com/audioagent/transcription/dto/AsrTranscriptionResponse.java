package com.audioagent.transcription.dto;

import lombok.Data;

import java.util.List;

@Data
public class AsrTranscriptionResponse {
    private String language;
    private Long durationMs;
    private String fullText;
    private Integer speakerCount;
    private List<AsrSegmentResponse> segments;
}
