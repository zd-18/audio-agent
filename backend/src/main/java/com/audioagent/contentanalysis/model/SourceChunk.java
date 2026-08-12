package com.audioagent.contentanalysis.model;

public record SourceChunk(
        String chunkId,
        Integer sourceSegmentOrder,
        Long startMs,
        Long endMs,
        String text,
        TimePrecision timePrecision) {
}
