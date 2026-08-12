package com.audioagent.analysis.silence;

import com.audioagent.analysis.exception.AudioAnalysisException;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegSilenceDetector implements SilenceDetector {

    private final AnalysisProperties properties;
    private final SilenceDetectOutputParser outputParser;
    private final ExternalProcessExecutor processExecutor;

    @Override
    public List<SilenceSegment> detect(Path inputFile, long audioDurationMs) {
        AnalysisProperties.Silence config = properties.getSilence();
        if (!config.isEnabled()) {
            return List.of();
        }
        validateConfiguration(config);
        if (!Files.isRegularFile(inputFile)) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                    false, "Audio input file is unavailable");
        }

        long startedAt = System.currentTimeMillis();
        try {
            ExternalProcessResult processResult = processExecutor.execute(
                    buildCommand(inputFile, config),
                    config.getTimeoutSeconds(),
                    line -> line.contains("silence_start:")
                            || line.contains("silence_end:"));

            if (log.isDebugEnabled()
                    && !processResult.debugOutput().isEmpty()) {
                log.debug("FFmpeg silencedetect output (truncated): {}",
                        processResult.debugOutput());
            }
            if (processResult.exitCode() != 0) {
                throw new AudioAnalysisException(
                        AudioAnalysisException.ErrorCodes
                                .SILENCE_DETECT_EXECUTION_FAILED,
                        true, "FFmpeg silence detection exited with code "
                        + processResult.exitCode());
            }

            try {
                List<SilenceSegment> segments = outputParser.parse(
                        processResult.selectedStderrLines(),
                        audioDurationMs, config.getMinDurationMs());
                log.debug("Silence detection parsed, elapsedMs={}, count={}",
                        System.currentTimeMillis() - startedAt,
                        segments.size());
                return segments;
            } catch (RuntimeException e) {
                throw new AudioAnalysisException(
                        AudioAnalysisException.ErrorCodes
                                .SILENCE_DETECT_PARSE_FAILED,
                        false, "FFmpeg silence detection output is invalid",
                        e);
            }
        } catch (AudioAnalysisException e) {
            throw e;
        } catch (ExternalProcessTimeoutException e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.SILENCE_DETECT_TIMEOUT,
                    true, "FFmpeg silence detection timed out", e);
        } catch (IOException e) {
            throw mapStartFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.PROCESS_INTERRUPTED,
                    true, "Silence detection was interrupted", e);
        } catch (Exception e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .SILENCE_DETECT_EXECUTION_FAILED,
                    true, "FFmpeg silence detection execution failed", e);
        }
    }

    List<String> buildCommand(Path inputFile,
                              AnalysisProperties.Silence config) {
        String threshold = config.getNoiseThresholdDb()
                .stripTrailingZeros().toPlainString();
        String minSeconds = BigDecimal.valueOf(config.getMinDurationMs())
                .movePointLeft(3).stripTrailingZeros().toPlainString();
        return List.of(
                properties.getFfmpegPath(),
                "-hide_banner",
                "-nostats",
                "-i", inputFile.toAbsolutePath().toString(),
                "-af", "silencedetect=n=" + threshold + "dB:d="
                        + minSeconds,
                "-f", "null",
                "-"
        );
    }

    private void validateConfiguration(AnalysisProperties.Silence config) {
        if (config.getMediumDurationMs() < config.getMinDurationMs()
                || config.getHighDurationMs()
                < config.getMediumDurationMs()) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.INVALID_CONFIGURATION,
                    false, "Invalid silence severity thresholds");
        }
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
                        .SILENCE_DETECT_EXECUTION_FAILED,
                !notFound,
                notFound ? "FFmpeg executable was not found"
                        : "Unable to start FFmpeg silence detection",
                e);
    }
}
