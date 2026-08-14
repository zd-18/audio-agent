package com.audioagent.processing.critic;

import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.pipeline.ProcessingOutputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingResultCriticTest {

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsGeneratedFileValidMetadataAndSuccessfulSteps()
            throws Exception {
        AudioProcessingProperties properties = properties();
        ProcessingResultCritic critic = new ProcessingResultCritic(
                new ProcessingOutputValidator(properties));
        Path output = Files.write(tempDirectory.resolve("result.wav"),
                new byte[64]);

        assertDoesNotThrow(() -> critic.review(output, metadata(5_000),
                5_000, List.of(step("SUCCESS")), 1));
    }

    @Test
    void rejectsAbnormalResultAndDoesNotRetry() throws Exception {
        ProcessingResultCritic critic = new ProcessingResultCritic(
                new ProcessingOutputValidator(properties()));
        Path output = Files.write(tempDirectory.resolve("result.wav"),
                new byte[64]);

        assertThrows(ProcessingExecutionException.class,
                () -> critic.review(output, metadata(8_000), 5_000,
                        List.of(step("SUCCESS")), 1));
        assertThrows(ProcessingExecutionException.class,
                () -> critic.review(output, metadata(5_000), 5_000,
                        List.of(step("FAILED")), 1));
    }

    @Test
    void acceptsDenoiseResultWithPreservedDurationAndSuccessfulSteps()
            throws Exception {
        ProcessingResultCritic critic = new ProcessingResultCritic(
                new ProcessingOutputValidator(properties()));
        Path output = Files.write(tempDirectory.resolve("result.wav"),
                new byte[64]);

        assertDoesNotThrow(() -> critic.review(output, metadata(5_000),
                5_000, List.of(step("SUCCESS", "DENOISE")), 1));
    }

    private AudioProcessingProperties properties() {
        AudioProcessingProperties properties = new AudioProcessingProperties();
        properties.getValidation().setMinimumOutputSizeBytes(1);
        properties.getValidation().setMinimumOutputDurationMs(1);
        properties.getValidation().setDurationToleranceMs(100);
        return properties;
    }

    private AudioMetadata metadata(long durationMs) {
        return AudioMetadata.builder().durationMs(durationMs)
                .channels(2).sampleRate(48_000).build();
    }

    private AudioProcessingExecutionStep step(String status) {
        return step(status, null);
    }

    private AudioProcessingExecutionStep step(String status,
                                              String operationType) {
        AudioProcessingExecutionStep step = new AudioProcessingExecutionStep();
        step.setStepOrder(1);
        step.setExecutionStatus(status);
        step.setOperationType(operationType);
        return step;
    }
}
