package com.audioagent.processing.pipeline;

import com.audioagent.analysis.silence.SilenceDetectOutputParser;
import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Detects long silent intervals of an audio file using FFmpeg's
 * silencedetect filter, reusing the same parser that the analysis module
 * uses so start/end-of-file silence is handled consistently.
 *
 * <p>The noise threshold is an internal configuration value (see
 * {@link AnalysisProperties.Silence#getNoiseThresholdDb()}); it is never
 * exposed as a user-facing parameter.
 */
@Component
@RequiredArgsConstructor
public class LongSilenceDetector {

    private static final int STDOUT_MAX_LINES = 5000;

    private final FfmpegCommandExecutor ffmpeg;
    private final SilenceDetectOutputParser parser;
    private final AnalysisProperties properties;

    public List<SilenceInterval> detect(Path input, long audioDurationMs,
                                        long minSilenceMs) {
        if (minSilenceMs <= 0) {
            throw new IllegalArgumentException(
                    "minSilenceMs must be positive");
        }
        BigDecimal noiseDb = properties.getSilence().getNoiseThresholdDb();
        String filter = "silencedetect=n=" + noiseDb + "dB:d="
                + FfmpegValueFormatter.seconds(minSilenceMs);
        String output = ffmpeg.analyzeLoudness(input, filter);
        List<SilenceSegment> segments = parser.parse(
                parseLines(output), audioDurationMs, minSilenceMs);
        return segments.stream()
                .sorted(Comparator.comparingLong(SilenceSegment::startMs)
                        .thenComparingLong(SilenceSegment::endMs))
                .map(segment -> new SilenceInterval(segment.startMs(),
                        segment.endMs(), segment.durationMs()))
                .toList();
    }

    private List<String> parseLines(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        String[] lines = output.split("\\r?\\n");
        int from = Math.max(0, lines.length - STDOUT_MAX_LINES);
        return Arrays.asList(Arrays.copyOfRange(lines, from, lines.length));
    }
}
