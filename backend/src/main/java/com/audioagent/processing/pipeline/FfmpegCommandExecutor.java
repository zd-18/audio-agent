package com.audioagent.processing.pipeline;

import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessResult;
import com.audioagent.analysis.process.ExternalProcessTimeoutException;
import com.audioagent.analysis.process.ExternalProcessCancelledException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.exception.ProcessingExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegCommandExecutor {

    private final AnalysisProperties analysisProperties;
    private final AudioProcessingProperties processingProperties;
    private final ExternalProcessExecutor processExecutor;

    public void transform(Path input, Path output, String audioFilter,
                          String complexFilter, String map,
                          String operationLabel) {
        List<String> command = base(input);
        if (audioFilter != null) {
            command.add("-af");
            command.add(audioFilter);
        }
        if (complexFilter != null) {
            command.add("-filter_complex");
            command.add(complexFilter);
        }
        if (map != null) {
            command.add("-map");
            command.add(map);
        }
        addOutputOptions(command);
        command.add(output.toAbsolutePath().toString());
        run(command, operationLabel);
    }

    public String analyzeLoudness(Path input, String filter) {
        List<String> command = base(input);
        command.add("-af");
        command.add(filter);
        command.add("-f");
        command.add("null");
        command.add("-");
        return run(command, "NORMALIZE_VOLUME_ANALYSIS").debugOutput();
    }

    private List<String> base(Path input) {
        List<String> command = new ArrayList<>();
        command.add(analysisProperties.getFfmpegPath());
        command.add("-y");
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("info");
        command.add("-i");
        command.add(input.toAbsolutePath().toString());
        command.add("-vn");
        return command;
    }

    private void addOutputOptions(List<String> command) {
        command.add("-c:a");
        command.add(processingProperties.getOutput().getCodec());
        command.add("-ar");
        command.add(Integer.toString(
                processingProperties.getOutput().getSampleRate()));
        switch (processingProperties.getOutput().getChannels()) {
            case "mono" -> {
                command.add("-ac");
                command.add("1");
            }
            case "stereo" -> {
                command.add("-ac");
                command.add("2");
            }
            default -> {
                // Preserve source channel count.
            }
        }
    }

    private ExternalProcessResult run(List<String> command,
                                      String operationLabel) {
        long startedAt = System.currentTimeMillis();
        log.info("Starting FFmpeg processing, operation={}, timeoutSeconds={}",
                operationLabel,
                processingProperties.getExecutionTimeoutSeconds());
        try {
            ExternalProcessResult result = processExecutor.execute(command,
                    processingProperties.getExecutionTimeoutSeconds(),
                    line -> {
                    });
            if (result.exitCode() != 0) {
                String summary = summarize(result.debugOutput());
                log.error("FFmpeg processing exited with non-zero code, "
                                + "operation={}, exitCode={}, stderrTail={}",
                        operationLabel, result.exitCode(), summary);
                boolean unsupported = summary.contains("No such filter")
                        || summary.contains("Error initializing filter")
                        || summary.contains("Option not found");
                throw new ProcessingExecutionException(unsupported
                        ? ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION
                        : ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                        false, unsupported
                        ? "The installed FFmpeg does not support a required filter"
                        : "FFmpeg exited with code " + result.exitCode()
                        + " during " + operationLabel);
            }
            log.info("FFmpeg processing completed, operation={}, "
                            + "exitCode=0, elapsedMs={}", operationLabel,
                    System.currentTimeMillis() - startedAt);
            return result;
        } catch (ProcessingExecutionException e) {
            throw e;
        } catch (ExternalProcessCancelledException e) {
            log.info("FFmpeg processing cancelled, operation={}",
                    operationLabel);
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_CANCELLED,
                    false, "Audio processing was cancelled", e);
        } catch (ExternalProcessTimeoutException e) {
            log.error("FFmpeg processing timed out, operation={}, "
                            + "timeoutSeconds={}", operationLabel,
                    processingProperties.getExecutionTimeoutSeconds(), e);
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                    true, "FFmpeg timed out during " + operationLabel, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("FFmpeg processing interrupted, operation={}",
                    operationLabel, e);
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                    true, "FFmpeg processing was interrupted", e);
        } catch (IOException e) {
            log.error("FFmpeg process could not start, operation={}",
                    operationLabel, e);
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                    true, "FFmpeg process could not be started", e);
        } catch (Exception e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED,
                    true, "FFmpeg process failed temporarily", e);
        }
    }

    private String summarize(String output) {
        if (output == null) {
            return "";
        }
        String sanitized = output.replace('\r', ' ').replace('\n', ' ');
        return sanitized.length() <= 500 ? sanitized
                : sanitized.substring(sanitized.length() - 500);
    }
}
