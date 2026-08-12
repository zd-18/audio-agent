package com.audioagent.analysis.silence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilenceDetectOutputParserTest {

    private final SilenceDetectOutputParser parser =
            new SilenceDetectOutputParser();

    @Test
    void parsesSingleSilenceAndRoundsDecimalSecondsSafely() {
        List<SilenceSegment> result = parser.parse(List.of(
                "[silencedetect] silence_start: 1.2345",
                "[silencedetect] silence_end: 3.0004 | silence_duration: 1.7659"
        ), 10_000, 1);

        assertEquals(List.of(new SilenceSegment(1235, 3000, 1765)),
                result);
    }

    @Test
    void parsesMultipleSegmentsAndIgnoresUnrelatedOrMalformedLines() {
        List<SilenceSegment> result = parser.parse(List.of(
                "ffmpeg version test",
                "silence_start: not-a-number",
                "[silencedetect] silence_start: 0",
                "[silencedetect] silence_end: 2.0 | silence_duration: 2.0",
                "random log",
                "[silencedetect] silence_start: 4.5",
                "[silencedetect] silence_end: 7.75 | silence_duration: 3.25"
        ), 10_000, 1_500);

        assertEquals(List.of(
                new SilenceSegment(0, 2000, 2000),
                new SilenceSegment(4500, 7750, 3250)
        ), result);
    }

    @Test
    void returnsEmptyListWhenThereAreNoSilenceEvents() {
        assertTrue(parser.parse(List.of("metadata", "progress"),
                5_000, 1_500).isEmpty());
    }

    @Test
    void completesTrailingSilenceWithAudioDurationAndClampsStart() {
        List<SilenceSegment> result = parser.parse(List.of(
                "silence_start: 7.125"
        ), 10_000, 1_500);
        List<SilenceSegment> clamped = parser.parse(List.of(
                "silence_start: 12"
        ), 10_000, 1);

        assertEquals(List.of(new SilenceSegment(7125, 10_000, 2875)),
                result);
        assertTrue(clamped.isEmpty());
    }

    @Test
    void filtersSegmentsBelowConfiguredMinimumDuration() {
        List<SilenceSegment> result = parser.parse(List.of(
                "silence_start: 1",
                "silence_end: 2.499 | silence_duration: 1.499"
        ), 5_000, 1_500);

        assertTrue(result.isEmpty());
    }
}
