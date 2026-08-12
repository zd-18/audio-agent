package com.audioagent.analysis.silence;

/**
 * A validated silence interval expressed in milliseconds.
 */
public record SilenceSegment(long startMs, long endMs, long durationMs) {
}
