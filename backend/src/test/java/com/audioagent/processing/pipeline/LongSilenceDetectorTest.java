package com.audioagent.processing.pipeline;

import com.audioagent.analysis.silence.SilenceDetectOutputParser;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongSilenceDetectorTest {

    private FfmpegCommandExecutor ffmpeg;
    private LongSilenceDetector detector;

    @BeforeEach
    void setUp() {
        ffmpeg = mock(FfmpegCommandExecutor.class);
        detector = new LongSilenceDetector(ffmpeg,
                new SilenceDetectOutputParser(), new AnalysisProperties());
    }

    @Test
    void parsesSilencedetectOutputIntoStructuredIntervals() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("""
                        [silencedetect @ 0x123] silence_start: 2.000
                        [silencedetect @ 0x123] silence_end: 5.000 | silence_duration: 3.000
                        [silencedetect @ 0x123] silence_start: 7.000
                        [silencedetect @ 0x123] silence_end: 11.000 | silence_duration: 4.000
                        """);

        List<SilenceInterval> intervals = detector.detect(
                Path.of("in.wav"), 13_000, 3_000);

        assertEquals(2, intervals.size());
        assertEquals(new SilenceInterval(2_000, 5_000, 3_000),
                intervals.get(0));
        assertEquals(new SilenceInterval(7_000, 11_000, 4_000),
                intervals.get(1));
    }

    @Test
    void handlesMultipleIntervalsAndSortsByStart() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("""
                        [silencedetect @ 0x1] silence_start: 9.000
                        [silencedetect @ 0x1] silence_end: 12.000 | silence_duration: 3.000
                        [silencedetect @ 0x1] silence_start: 1.000
                        [silencedetect @ 0x1] silence_end: 4.000 | silence_duration: 3.000
                        """);

        List<SilenceInterval> intervals = detector.detect(
                Path.of("in.wav"), 13_000, 3_000);

        assertEquals(2, intervals.size());
        assertEquals(1_000, intervals.get(0).startMs());
        assertEquals(9_000, intervals.get(1).startMs());
    }

    @Test
    void includesLeadingAndTrailingSilenceAtFileBoundaries() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("""
                        [silencedetect @ 0x1] silence_start: 0.000
                        [silencedetect @ 0x1] silence_end: 4.000 | silence_duration: 4.000
                        [silencedetect @ 0x1] silence_start: 9.000
                        """);

        // 结尾静音无 silence_end 行：解析器用音频总时长补齐
        List<SilenceInterval> intervals = detector.detect(
                Path.of("in.wav"), 13_000, 3_000);

        assertEquals(2, intervals.size());
        assertEquals(new SilenceInterval(0, 4_000, 4_000),
                intervals.get(0));
        assertEquals(new SilenceInterval(9_000, 13_000, 4_000),
                intervals.get(1));
    }

    @Test
    void returnsEmptyWhenNoLongSilenceDetected() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("""
                        [silencedetect @ 0x1] silence_start: 2.000
                        [silencedetect @ 0x1] silence_end: 3.500 | silence_duration: 1.500
                        """);

        List<SilenceInterval> intervals = detector.detect(
                Path.of("in.wav"), 10_000, 3_000);

        assertTrue(intervals.isEmpty());
    }

    @Test
    void filtersIntervalsShorterThanMinSilence() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("""
                        [silencedetect @ 0x1] silence_start: 1.000
                        [silencedetect @ 0x1] silence_end: 3.500 | silence_duration: 2.500
                        [silencedetect @ 0x1] silence_start: 5.000
                        [silencedetect @ 0x1] silence_end: 10.000 | silence_duration: 5.000
                        """);

        List<SilenceInterval> intervals = detector.detect(
                Path.of("in.wav"), 12_000, 3_000);

        assertEquals(1, intervals.size());
        assertEquals(new SilenceInterval(5_000, 10_000, 5_000),
                intervals.get(0));
    }

    @Test
    void passesConfiguredThresholdAndNoiseFloorToFfmpeg() {
        when(ffmpeg.analyzeLoudness(eq(Path.of("in.wav")), anyString()))
                .thenReturn("");

        detector.detect(Path.of("in.wav"), 10_000, 3_000);

        verify(ffmpeg).analyzeLoudness(eq(Path.of("in.wav")),
                eq("silencedetect=n=-45dB:d=3"));
    }
}
