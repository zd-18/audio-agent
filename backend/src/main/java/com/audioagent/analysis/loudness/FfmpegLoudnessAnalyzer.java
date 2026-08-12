package com.audioagent.analysis.loudness;

import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.noise.NoiseAnalysisFrame;
import com.audioagent.analysis.noise.NoiseFrameOutputParser;
import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessResult;
import com.audioagent.analysis.process.ExternalProcessTimeoutException;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegLoudnessAnalyzer implements LoudnessAnalyzer {

    private final AnalysisProperties properties;
    private final Ebur128OutputParser outputParser;
    private final NoiseFrameOutputParser noiseOutputParser;
    private final ExternalProcessExecutor processExecutor;

    @Override
    public Optional<LoudnessAnalysis> analyze(Path inputFile) {
        AnalysisProperties.Loudness config = properties.getLoudness();
        boolean noiseEnabled = properties.getNoiseRisk().isEnabled();
        if (!config.isEnabled()
                && !properties.getVolumeSegment().isEnabled()
                && !noiseEnabled) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(inputFile)) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                    false, "Audio input file is unavailable");
        }

        Ebur128OutputParser.Accumulator accumulator =
                outputParser.newAccumulator();
        NoiseFrameOutputParser.Accumulator noiseAccumulator =
                noiseOutputParser.newAccumulator(
                        noiseFrameSampleCount());
        try {
            ExternalProcessResult processResult = processExecutor.execute(
                    buildCommand(inputFile), resolveTimeoutSeconds(),
                    line -> {
                        accumulator.accept(line);
                        if (noiseEnabled) {
                            noiseAccumulator.accept(line);
                        }
                    });

            if (log.isDebugEnabled()
                    && !processResult.debugOutput().isEmpty()) {
                log.debug("FFmpeg ebur128 output (truncated): {}",
                        processResult.debugOutput());
            }
            if (processResult.exitCode() != 0) {
                throw new AudioAnalysisException(
                        AudioAnalysisException.ErrorCodes
                                .LOUDNESS_ANALYSIS_EXECUTION_FAILED,
                        true, "FFmpeg loudness analysis exited with code "
                        + processResult.exitCode());
            }

            try {
                LoudnessAnalysis loudness = accumulator.finish();
                List<NoiseAnalysisFrame> noiseFrames = noiseEnabled
                        ? noiseAccumulator.finish() : List.of();
                LoudnessAnalysis analysis = new LoudnessAnalysis(
                        loudness.metrics(), loudness.frames(), noiseFrames);
                if (analysis.frames().isEmpty()) {
                    log.warn("No valid ebur128 time-series frames were "
                            + "parsed; whole-file metrics remain available");
                }
                return Optional.of(analysis);
            } catch (RuntimeException e) {
                throw new AudioAnalysisException(
                        AudioAnalysisException.ErrorCodes
                                .LOUDNESS_ANALYSIS_PARSE_FAILED,
                        false, "FFmpeg loudness Summary is invalid", e);
            }
        } catch (AudioAnalysisException e) {
            throw e;
        } catch (ExternalProcessTimeoutException e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .LOUDNESS_ANALYSIS_TIMEOUT,
                    true, "FFmpeg loudness analysis timed out", e);
        } catch (IOException e) {
            throw mapStartFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.PROCESS_INTERRUPTED,
                    true, "Loudness analysis was interrupted", e);
        } catch (Exception e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .LOUDNESS_ANALYSIS_EXECUTION_FAILED,
                    true, "FFmpeg loudness analysis execution failed", e);
        }
    }

    List<String> buildCommand(Path inputFile) {
        boolean metadataEnabled = properties.getVolumeSegment().isEnabled()
                || properties.getNoiseRisk().isEnabled();
        StringBuilder filter = new StringBuilder("ebur128=");
        if (metadataEnabled) {
            filter.append("metadata=1:");
        }
        filter.append("peak=sample+true");
        if (properties.getNoiseRisk().isEnabled()) {
            AnalysisProperties.NoiseRisk noise = properties.getNoiseRisk();
            String frameSeconds = BigDecimal.valueOf(
                    noise.getFrameDurationMs()).movePointLeft(3)
                    .stripTrailingZeros().toPlainString();
            int frameSamples = noiseFrameSampleCount();
            filter.append(",aresample=48000,asetnsamples=n=")
                    .append(frameSamples).append(":p=1")
                    .append(",astats=metadata=1:reset=1:length=")
                    .append(frameSeconds)
                    .append(":measure_perchannel=none")
                    .append(":measure_overall=RMS_level+Peak_level+Noise_floor")
                    .append(",aspectralstats=win_size=")
                    .append(frameSamples)
                    .append(":overlap=0:measure=centroid+flatness+entropy");
        }
        if (metadataEnabled) {
            filter.append(",ametadata=mode=print");
        }
        return List.of(
                properties.getFfmpegPath(),
                "-hide_banner",
                "-nostats",
                "-i", inputFile.toAbsolutePath().toString(),
                "-filter_complex", filter.toString(),
                "-f", "null",
                "-"
        );
    }

    private int resolveTimeoutSeconds() {
        int timeout = properties.getLoudness().getTimeoutSeconds();
        if (properties.getNoiseRisk().isEnabled()) {
            timeout = Math.max(timeout,
                    properties.getNoiseRisk().getTimeoutSeconds());
        }
        return timeout;
    }

    private int noiseFrameSampleCount() {
        return Math.toIntExact(properties.getNoiseRisk()
                .getFrameDurationMs() * 48L);
    }

    private AudioAnalysisException mapStartFailure(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        boolean notFound = message.contains("error=2")
                || message.contains("No such file")
                || message.contains("cannot find the file");
        return new AudioAnalysisException(
                notFound
                        ? AudioAnalysisException.ErrorCodes.FFMPEG_NOT_FOUND
                        : AudioAnalysisException.ErrorCodes
                        .LOUDNESS_ANALYSIS_EXECUTION_FAILED,
                !notFound,
                notFound ? "FFmpeg executable was not found"
                        : "Unable to start FFmpeg loudness analysis",
                e);
    }
}
