package com.audioagent.processing.pipeline;

/**
 * A detected long silence interval expressed in milliseconds.
 *
 * @param startMs    inclusive start of the silent interval
 * @param endMs      exclusive end of the silent interval
 * @param durationMs length of the interval (endMs - startMs)
 */
public record SilenceInterval(long startMs, long endMs, long durationMs) {
}
