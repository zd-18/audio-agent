package com.audioagent.transcription.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AsrSegmentResponse {
    private Integer order;
    private Long startMs;
    private Long endMs;
    private String speaker;
    private String text;
    private BigDecimal confidence;
}
