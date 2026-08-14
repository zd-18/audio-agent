package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.audioagent.processing.model.ProcessingExecutionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AudioProcessingPipeline {

    private final SilenceTrimProcessor trimProcessor;
    private final DenoiseProcessor denoiseProcessor;
    private final LoudnessNormalizeProcessor loudnessProcessor;
    private final ProcessingOutputValidator outputValidator;
    private final AudioMetadataProbe metadataProbe;
    private final FfmpegCommandExecutor ffmpeg;

    public ProcessingOutput execute(
            Path input, List<ExecutableProcessingStep> steps,
            Path workDirectory) {
        return execute(input, steps, workDirectory, (stage, progress) -> {
        });
    }

    public ProcessingOutput execute(
            Path input, List<ExecutableProcessingStep> steps,
            Path workDirectory, ProcessingProgressListener progress) {
        validatePaths(input, workDirectory);
        List<ExecutableProcessingStep> safeSteps = steps == null
                ? List.of() : List.copyOf(steps);
        validateOperations(safeSteps);
        AudioMetadata sourceMetadata = probe(input);
        long expectedDuration = requireDuration(sourceMetadata);
        Path current = input;
        boolean transformed = false;

        List<ExecutableProcessingStep> trims = safeSteps.stream()
                .filter(step -> step.operationType()
                        == ProcessingOperationType.TRIM_SEGMENT)
                .toList();
        if (!trims.isEmpty()) {
            progress.onStage(ProcessingExecutionStage.TRIMMING, 45);
            Path output = workDirectory.resolve("stage-trim.wav");
            SilenceTrimPlanner.Plan plan = trimProcessor.process(current,
                    output, expectedDuration, trims);
            expectedDuration = plan.retainedDurationMs();
            outputValidator.validateFile(output);
            current = output;
            transformed = true;
        }

        ExecutableProcessingStep denoise = single(safeSteps,
                ProcessingOperationType.DENOISE);
        if (denoise != null) {
            progress.onStage(ProcessingExecutionStage.DENOISING, 55);
            Path output = workDirectory.resolve("stage-denoised.wav");
            denoiseProcessor.process(current, output, denoise);
            outputValidator.validateFile(output);
            current = output;
            transformed = true;
        }

        ExecutableProcessingStep normalization = single(safeSteps,
                ProcessingOperationType.NORMALIZE_VOLUME);
        if (normalization != null) {
            progress.onStage(
                    ProcessingExecutionStage.LOUDNESS_NORMALIZING, 70);
            Path output = workDirectory.resolve("stage-normalized.wav");
            loudnessProcessor.process(current, output, normalization);
            outputValidator.validateFile(output);
            current = output;
            transformed = true;
        }

        Path result = workDirectory.resolve("result.wav");
        if (transformed) {
            try {
                Files.move(current, result,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new ProcessingExecutionException(
                        ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID,
                        true, "Final processing output could not be prepared",
                        e);
            }
        } else {
            ffmpeg.transform(input, result, "anull", null, null,
                    "OUTPUT_MATERIALIZATION");
        }
        outputValidator.validateFile(result);
        return new ProcessingOutput(result, expectedDuration);
    }

    private void validateOperations(List<ExecutableProcessingStep> steps) {
        for (ExecutableProcessingStep step : steps) {
            if (step == null || step.operationType() == null
                    || !step.operationType().isExecutable()) {
                throw new ProcessingExecutionException(
                        ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION,
                        false, "Only NORMALIZE_VOLUME, TRIM_SEGMENT and DENOISE are supported");
            }
        }
    }

    private ExecutableProcessingStep single(
            List<ExecutableProcessingStep> steps,
            ProcessingOperationType operation) {
        List<ExecutableProcessingStep> matching = steps.stream()
                .filter(step -> step.operationType() == operation)
                .sorted(Comparator.comparing(
                        ExecutableProcessingStep::stepOrder))
                .toList();
        if (matching.size() > 1) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false, operation + " may appear at most once");
        }
        return matching.isEmpty() ? null : matching.getFirst();
    }

    private AudioMetadata probe(Path input) {
        try {
            return metadataProbe.probe(input);
        } catch (RuntimeException e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                    false, "Source audio cannot be read by ffprobe", e);
        }
    }

    private long requireDuration(AudioMetadata metadata) {
        if (metadata == null || metadata.getDurationMs() == null
                || metadata.getDurationMs() <= 0) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                    false, "Source audio duration is invalid");
        }
        return metadata.getDurationMs();
    }

    private void validatePaths(Path input, Path workDirectory) {
        if (input == null || !Files.isRegularFile(input)
                || workDirectory == null
                || !Files.isDirectory(workDirectory)) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND,
                    false, "Processing input or work directory is invalid");
        }
    }

}
