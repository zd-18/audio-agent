package com.audioagent.processing.pipeline;

import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.exception.ProcessingExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ProcessingOutputValidator {

    private final AudioProcessingProperties properties;

    public void validateFile(Path output) {
        try {
            if (!Files.isRegularFile(output)
                    || Files.size(output) < properties.getValidation()
                    .getMinimumOutputSizeBytes()) {
                throw invalid("Processing output file is empty or too small");
            }
        } catch (IOException e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID,
                    false, "Processing output file cannot be read", e);
        }
    }

    public void validateMetadata(AudioMetadata metadata,
                                 long expectedDurationMs) {
        if (metadata == null || metadata.getDurationMs() == null
                || metadata.getDurationMs() < properties.getValidation()
                .getMinimumOutputDurationMs()
                || metadata.getChannels() == null
                || metadata.getChannels() <= 0
                || metadata.getSampleRate() == null
                || metadata.getSampleRate() <= 0) {
            throw invalid("Processed audio metadata is invalid");
        }
        long tolerance = properties.getValidation()
                .getDurationToleranceMs();
        if (Math.abs(metadata.getDurationMs() - expectedDurationMs)
                > tolerance) {
            throw invalid("Processed audio duration is outside tolerance");
        }
    }

    private ProcessingExecutionException invalid(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID,
                false, message);
    }
}
