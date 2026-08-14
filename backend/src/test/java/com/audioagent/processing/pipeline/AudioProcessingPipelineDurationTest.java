package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioProcessingPipelineDurationTest {

    @TempDir
    Path workDirectory;

    private LoudnessNormalizeProcessor loudnessProcessor;
    private ProcessingOutputValidator outputValidator;
    private AudioMetadataProbe metadataProbe;
    private FfmpegCommandExecutor ffmpeg;
    private AudioProcessingPipeline pipeline;
    private Path source;

    @BeforeEach
    void setUp() throws Exception {
        source = workDirectory.resolve("source.mp4");
        Files.write(source, new byte[]{1});
        loudnessProcessor = mock(LoudnessNormalizeProcessor.class);
        outputValidator = mock(ProcessingOutputValidator.class);
        metadataProbe = mock(AudioMetadataProbe.class);
        ffmpeg = mock(FfmpegCommandExecutor.class);
        pipeline = new AudioProcessingPipeline(
                new SilenceTrimProcessor(ffmpeg,
                        new SilenceTrimPlanner()),
                loudnessProcessor, outputValidator, metadataProbe, ffmpeg);
        when(metadataProbe.probe(source)).thenReturn(sourceMetadata());
    }

    @Test
    void normalizeVolumeUsesSelectedAudioStreamDurationAsExpected()
            throws Exception {
        ExecutableProcessingStep normalization = step(
                ProcessingOperationType.NORMALIZE_VOLUME, null, null,
                Map.of("targetLufs", -16, "truePeakLimitDbfs", -1));
        doAnswer(invocation -> {
            Files.write(invocation.getArgument(1), new byte[]{2});
            return null;
        }).when(loudnessProcessor).process(eq(source), any(Path.class),
                eq(normalization));

        ProcessingOutput output = pipeline.execute(source,
                List.of(normalization), workDirectory);

        assertEquals(76_409L, output.expectedDurationMs());
        verify(loudnessProcessor).process(eq(source), any(Path.class),
                eq(normalization));
    }

    @Test
    void trimSegmentCalculatesRetainedDurationFromSelectedStreamDuration()
            throws Exception {
        ExecutableProcessingStep trim = step(
                ProcessingOperationType.TRIM_SEGMENT, 1_000L, 2_000L,
                Map.of());
        doAnswer(invocation -> {
            Files.write(invocation.getArgument(1), new byte[]{2});
            return null;
        }).when(ffmpeg).transform(eq(source), any(Path.class), eq(null),
                any(String.class), eq("[outa]"), eq("TRIM_SEGMENT"));

        ProcessingOutput output = pipeline.execute(source, List.of(trim),
                workDirectory);

        assertEquals(75_409L, output.expectedDurationMs());
    }

    private AudioMetadata sourceMetadata() {
        return AudioMetadata.builder()
                .formatDurationMs(79_931L)
                .streamDurationMs(76_409L)
                .durationMs(76_409L)
                .sampleRate(44_100)
                .channels(2)
                .build();
    }

    private ExecutableProcessingStep step(
            ProcessingOperationType operation, Long startMs, Long endMs,
            Map<String, Object> parameters) {
        return new ExecutableProcessingStep(1L, 1, operation, startMs,
                endMs, parameters);
    }
}
