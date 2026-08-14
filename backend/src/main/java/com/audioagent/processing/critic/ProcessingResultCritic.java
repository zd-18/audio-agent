package com.audioagent.processing.critic;

import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.pipeline.ProcessingOutputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * First-version rule-only Critic. It never calls an AI model and never retries.
 */
@Component
@RequiredArgsConstructor
public class ProcessingResultCritic {

    private final ProcessingOutputValidator outputValidator;

    public void review(Path output, AudioMetadata metadata,
                       long expectedDurationMs,
                       List<AudioProcessingExecutionStep> steps,
                       int expectedStepCount) {
        outputValidator.validateFile(output);
        outputValidator.validateMetadata(metadata, expectedDurationMs);
        if (steps == null || steps.size() != expectedStepCount) {
            throw invalid("Not all processing steps produced a result");
        }
        for (AudioProcessingExecutionStep step : steps) {
            if (step == null || !"SUCCESS".equals(
                    step.getExecutionStatus())) {
                String order = step == null || step.getStepOrder() == null
                        ? "unknown" : step.getStepOrder().toString();
                throw invalid("Processing step " + order
                        + " did not complete successfully");
            }
        }
    }

    private ProcessingExecutionException invalid(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID,
                false, message);
    }
}
