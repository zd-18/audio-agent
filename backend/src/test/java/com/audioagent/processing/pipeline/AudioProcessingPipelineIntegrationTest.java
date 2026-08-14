package com.audioagent.processing.pipeline;

import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ProcessBuilderExternalProcessStarter;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.FfprobeAudioMetadataProbe;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioProcessingPipelineIntegrationTest {

    private static final Path FFMPEG =
            Path.of("D:/ffmpeg/bin/ffmpeg.exe");
    private static final Path FFPROBE =
            Path.of("D:/ffmpeg/bin/ffprobe.exe");

    @TempDir
    Path tempDirectory;

    private AudioProcessingPipeline pipeline;
    private FfprobeAudioMetadataProbe metadataProbe;
    private ProcessingOutputValidator outputValidator;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Files.isRegularFile(FFMPEG)
                && Files.isRegularFile(FFPROBE));
        AnalysisProperties analysis = new AnalysisProperties();
        analysis.setFfmpegPath(FFMPEG.toString());
        analysis.setFfprobePath(FFPROBE.toString());
        analysis.setTimeoutSeconds(30);
        AudioProcessingProperties processing =
                new AudioProcessingProperties();
        processing.setExecutionTimeoutSeconds(60);
        ObjectMapper mapper = new ObjectMapper();
        ExternalProcessExecutor processes = new ExternalProcessExecutor(
                new ProcessBuilderExternalProcessStarter());
        FfmpegCommandExecutor commands = new FfmpegCommandExecutor(
                analysis, processing, processes);
        metadataProbe = new FfprobeAudioMetadataProbe(analysis, mapper);
        outputValidator = new ProcessingOutputValidator(processing);
        pipeline = new AudioProcessingPipeline(
                new SilenceTrimProcessor(commands,
                        new SilenceTrimPlanner()),
                new DenoiseProcessor(commands, processing),
                new LoudnessNormalizeProcessor(commands,
                        new LoudnormOutputParser(mapper), processing),
                outputValidator,
                metadataProbe, commands);
    }

    @Test
    void normalizeVolumeCreatesNewAudioAndKeepsSourceUnchanged()
            throws Exception {
        Path source = tempDirectory.resolve("normalize-source.wav");
        generateFiveSecondTone(source, 0.12);
        String sourceHash = sha256(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.NORMALIZE_VOLUME,
                        null, null, Map.of("targetLufs", -16,
                                "truePeakLimitDbfs", -1))),
                tempDirectory);

        assertTrue(Files.isRegularFile(output.path()));
        assertTrue(Files.size(output.path()) > 1024);
        assertEquals(sourceHash, sha256(source));
        assertNotEquals(source.toAbsolutePath(), output.path().toAbsolutePath());
        assertEquals(5_000L, output.expectedDurationMs());
    }

    @Test
    void trimSegmentRemovesExactStartEndRange() throws Exception {
        Path source = tempDirectory.resolve("trim-source.wav");
        generateFiveSecondTone(source, 0.5);
        String sourceHash = sha256(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.TRIM_SEGMENT,
                        1_250L, 3_250L, Map.of())), tempDirectory);
        AudioMetadata resultMetadata = metadataProbe.probe(output.path());

        assertEquals(3_000L, output.expectedDurationMs());
        assertTrue(Math.abs(resultMetadata.getDurationMs() - 3_000L)
                <= 100L, "actual duration="
                + resultMetadata.getDurationMs());
        assertEquals(sourceHash, sha256(source));
    }

    @Test
    void denoiseReducesNoiseKeepsDurationAndKeepsSourceUnchanged()
            throws Exception {
        Path source = tempDirectory.resolve("noisy-source.wav");
        generateNoisyTone(source);
        String sourceHash = sha256(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.DENOISE, null, null,
                        Map.of("strength", "MEDIUM"))), tempDirectory);
        AudioMetadata resultMetadata = metadataProbe.probe(output.path());

        assertTrue(Files.isRegularFile(output.path()));
        assertTrue(Files.size(output.path()) > 1024);
        assertEquals(5_000L, output.expectedDurationMs());
        assertTrue(Math.abs(resultMetadata.getDurationMs() - 5_000L)
                <= 100L, "actual duration="
                + resultMetadata.getDurationMs());
        assertEquals(sourceHash, sha256(source));
    }

    @Test
    void denoiseCombinedWithTrimAndNormalizeProducesValidResult()
            throws Exception {
        Path source = tempDirectory.resolve("noisy-trim-source.wav");
        generateNoisyTone(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.TRIM_SEGMENT,
                        500L, 1_500L, Map.of()),
                step(2, ProcessingOperationType.DENOISE, null, null,
                        Map.of("strength", "LIGHT")),
                step(3, ProcessingOperationType.NORMALIZE_VOLUME,
                        null, null, Map.of("targetLufs", -16,
                                "truePeakLimitDbfs", -1))),
                tempDirectory);
        AudioMetadata resultMetadata = metadataProbe.probe(output.path());

        assertEquals(4_000L, output.expectedDurationMs());
        assertTrue(Math.abs(resultMetadata.getDurationMs() - 4_000L)
                <= 100L, "actual duration="
                + resultMetadata.getDurationMs());
        outputValidator.validateMetadata(resultMetadata,
                output.expectedDurationMs());
    }

    @Test
    void probePrefersFirstAudioStreamDurationOverContainerDuration()
            throws Exception {
        Path source = tempDirectory.resolve("different-durations.mp4");
        generateVideoWithShorterAudio(source);

        AudioMetadata sourceMetadata = metadataProbe.probe(source);
        Path materialized = tempDirectory.resolve("materialized.wav");
        materializeAudio(source, materialized);
        AudioMetadata materializedMetadata = metadataProbe.probe(materialized);

        assertEquals(sourceMetadata.getStreamDurationMs(),
                sourceMetadata.getDurationMs());
        assertTrue(sourceMetadata.getFormatDurationMs()
                - sourceMetadata.getStreamDurationMs() > 1_500L);
        assertTrue(Math.abs(materializedMetadata.getDurationMs()
                - sourceMetadata.getStreamDurationMs()) <= 250L,
                "formatDurationMs=" + sourceMetadata.getFormatDurationMs()
                        + ", streamDurationMs="
                        + sourceMetadata.getStreamDurationMs()
                        + ", materializedDurationMs="
                        + materializedMetadata.getDurationMs());
    }

    @Test
    void normalizeVolumeUsesAudioStreamDurationAsExpectedDuration()
            throws Exception {
        Path source = tempDirectory.resolve("normalize-video.mp4");
        generateVideoWithShorterAudio(source);
        AudioMetadata sourceMetadata = metadataProbe.probe(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.NORMALIZE_VOLUME,
                        null, null, Map.of("targetLufs", -16,
                                "truePeakLimitDbfs", -1))),
                tempDirectory);
        AudioMetadata resultMetadata = metadataProbe.probe(output.path());

        assertEquals(sourceMetadata.getStreamDurationMs(),
                output.expectedDurationMs());
        outputValidator.validateMetadata(resultMetadata,
                output.expectedDurationMs());
    }

    @Test
    void trimSegmentCalculatesExpectedDurationFromAudioStream()
            throws Exception {
        Path source = tempDirectory.resolve("trim-video.mp4");
        generateVideoWithShorterAudio(source);

        ProcessingOutput output = pipeline.execute(source, List.of(
                step(1, ProcessingOperationType.TRIM_SEGMENT,
                        500L, 1_500L, Map.of())), tempDirectory);
        AudioMetadata resultMetadata = metadataProbe.probe(output.path());

        assertEquals(2_000L, output.expectedDurationMs());
        outputValidator.validateMetadata(resultMetadata,
                output.expectedDurationMs());
    }

    private ExecutableProcessingStep step(
            int order, ProcessingOperationType operation,
            Long start, Long end, Map<String, Object> parameters) {
        return new ExecutableProcessingStep((long) order, order, operation,
                start, end, parameters);
    }

    private void generateNoisyTone(Path output) throws Exception {
        runFfmpeg("-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=48000:duration=5",
                "-f", "lavfi", "-i",
                "anoisesrc=d=5:r=48000:a=0.4",
                "-filter_complex", "[0:a][1:a]amix=inputs=2:duration=longest",
                "-c:a", "pcm_s16le", output.toString());
    }

    private void generateFiveSecondTone(Path output, double volume)
            throws Exception {
        Process process = new ProcessBuilder(FFMPEG.toString(), "-y",
                "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                "-i", "sine=frequency=440:sample_rate=48000:duration=5",
                "-af", "volume=" + volume, "-c:a", "pcm_s16le",
                output.toString()).redirectErrorStream(true).start();
        String details = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), details);
    }

    private void generateVideoWithShorterAudio(Path output)
            throws Exception {
        runFfmpeg("-f", "lavfi", "-i",
                "color=c=black:s=16x16:r=25:d=5", "-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=44100:duration=3",
                "-map", "0:v:0", "-map", "1:a:0", "-c:v", "mpeg4",
                "-c:a", "aac", output.toString());
    }

    private void materializeAudio(Path input, Path output)
            throws Exception {
        runFfmpeg("-i", input.toString(), "-map", "0:a:0", "-vn",
                "-af", "anull", "-c:a", "pcm_s16le", output.toString());
    }

    private void runFfmpeg(String... arguments) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add(FFMPEG.toString());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true).start();
        String details = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), details);
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
