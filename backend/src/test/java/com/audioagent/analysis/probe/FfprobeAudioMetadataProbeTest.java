package com.audioagent.analysis.probe;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FfprobeAudioMetadataProbeTest {

    private FfprobeAudioMetadataProbe probe;

    @BeforeEach
    void setUp() {
        probe = new FfprobeAudioMetadataProbe(
                new AnalysisProperties(), new ObjectMapper());
    }

    @Test
    void validAudioStreamDurationTakesPrecedenceOverFormatDuration() {
        AudioMetadata metadata = probe.parseOutput(jsonWithStreamDuration(
                "76.409002"));

        assertEquals(79_931L, metadata.getFormatDurationMs());
        assertEquals(76_409L, metadata.getStreamDurationMs());
        assertEquals(76_409L, metadata.getDurationMs());
    }

    @Test
    void validAudioStreamDurationDoesNotRequireValidFormatDuration() {
        AudioMetadata metadata = probe.parseOutput("""
                {
                  "streams": [{
                    "codec_name": "aac",
                    "duration": "76.409002",
                    "sample_rate": "44100",
                    "channels": 2
                  }],
                  "format": {
                    "duration": "N/A",
                    "format_name": "mov,mp4,m4a,3gp,3g2,mj2"
                  }
                }
                """);

        assertNull(metadata.getFormatDurationMs());
        assertEquals(76_409L, metadata.getDurationMs());
    }

    @Test
    void missingAudioStreamDurationFallsBackToFormatDuration() {
        AudioMetadata metadata = probe.parseOutput("""
                {
                  "streams": [{
                    "codec_name": "aac",
                    "sample_rate": "44100",
                    "channels": 2
                  }],
                  "format": {
                    "duration": "79.931000",
                    "format_name": "mov,mp4,m4a,3gp,3g2,mj2"
                  }
                }
                """);

        assertNull(metadata.getStreamDurationMs());
        assertEquals(79_931L, metadata.getDurationMs());
    }

    @Test
    void invalidAudioStreamDurationFallsBackToFormatDuration() {
        AudioMetadata metadata = probe.parseOutput(jsonWithStreamDuration(
                "N/A"));

        assertNull(metadata.getStreamDurationMs());
        assertEquals(79_931L, metadata.getDurationMs());
    }

    @Test
    void nonPositiveAudioStreamDurationFallsBackToFormatDuration() {
        AudioMetadata metadata = probe.parseOutput(jsonWithStreamDuration(
                "0.000000"));

        assertEquals(0L, metadata.getStreamDurationMs());
        assertEquals(79_931L, metadata.getDurationMs());
    }

    @Test
    void negativeAudioStreamDurationFallsBackToFormatDuration() {
        AudioMetadata metadata = probe.parseOutput(jsonWithStreamDuration(
                "-1.000000"));

        assertEquals(-1_000L, metadata.getStreamDurationMs());
        assertEquals(79_931L, metadata.getDurationMs());
    }

    private String jsonWithStreamDuration(String duration) {
        return """
                {
                  "streams": [{
                    "codec_name": "aac",
                    "duration": "%s",
                    "sample_rate": "44100",
                    "channels": 2
                  }],
                  "format": {
                    "duration": "79.931000",
                    "format_name": "mov,mp4,m4a,3gp,3g2,mj2"
                  }
                }
                """.formatted(duration);
    }
}
