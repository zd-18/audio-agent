package com.audioagent.processing.snapshot;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingStepDecision;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProcessingExecutionSnapshotParser {

    private static final BigDecimal MIN_TARGET_LUFS =
            BigDecimal.valueOf(-24);
    private static final BigDecimal MAX_TARGET_LUFS =
            BigDecimal.valueOf(-8);
    private static final BigDecimal MIN_SILENCE_MS = BigDecimal.valueOf(1000);
    private static final BigDecimal MAX_SILENCE_MS = BigDecimal.valueOf(60000);
    private static final BigDecimal MIN_KEEP_SILENCE_MS = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_KEEP_SILENCE_MS = BigDecimal.valueOf(3000);

    private final ObjectMapper objectMapper;

    public ProcessingExecutionSnapshot parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalidSnapshot("Confirmed execution snapshot is missing");
        }
        ProcessingConfirmationVO snapshot;
        try {
            snapshot = objectMapper.readValue(json,
                    ProcessingConfirmationVO.class);
        } catch (Exception e) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false, "Confirmed execution snapshot is invalid", e);
        }
        requirePositive(snapshot.getConfirmationId(), "confirmationId");
        requirePositive(snapshot.getTaskId(), "taskId");
        requirePositive(snapshot.getAudioFileId(), "audioFileId");
        requirePositive(snapshot.getPlanId(), "planId");
        if (!"CONFIRMED".equals(snapshot.getConfirmationStatus())
                || snapshot.getSourcePlanRevision() == null
                || snapshot.getSourcePlanRevision() <= 0) {
            throw invalidSnapshot("Snapshot is not a confirmed plan revision");
        }

        List<ProcessingExecutionSnapshot.Step> accepted = new ArrayList<>();
        int wholeAudioCount = 0;
        for (ProcessingConfirmationVO.Step step : safe(snapshot.getSteps())) {
            if (!ProcessingStepDecision.ACCEPTED.name().equals(
                    step.getDecision())) {
                continue;
            }
            requirePositive(step.getStepConfirmationId(),
                    "stepConfirmationId");
            requirePositive(step.getSourceStepId(), "sourceStepId");
            if (step.getStepOrder() == null || step.getStepOrder() <= 0) {
                throw invalidSnapshot("stepOrder is missing from the snapshot");
            }
            ProcessingOperationType operation = supportedOperation(
                    step.getOperationType());
            Map<String, Object> parameters;
            try {
                parameters = step.getEffectiveParameters() == null
                        ? Map.of() : Map.copyOf(
                        step.getEffectiveParameters());
            } catch (RuntimeException e) {
                throw invalidParameter(
                        "Effective parameters contain invalid values");
            }
            switch (operation) {
                case TRIM_SEGMENT -> requireRange(step);
                case NORMALIZE_VOLUME -> validateNormalization(parameters);
                case DENOISE -> validateDenoiseStrength(parameters);
                case SILENCE_CLEANUP -> validateSilenceCleanup(parameters);
                default -> throw unsupported(operation.name());
            }
            if (operation != ProcessingOperationType.TRIM_SEGMENT) {
                wholeAudioCount += 1;
                if (wholeAudioCount > 1) {
                    throw invalidParameter(
                            "Whole-audio operations may appear at most once");
                }
            }
            accepted.add(new ProcessingExecutionSnapshot.Step(
                    step.getStepConfirmationId(), step.getSourceStepId(),
                    step.getStepOrder(), operation.name(),
                    step.getUserConfirmed(), step.getStartMs(),
                    step.getEndMs(), parameters));
        }
        return new ProcessingExecutionSnapshot(snapshot.getConfirmationId(),
                snapshot.getTaskId(), snapshot.getAudioFileId(),
                snapshot.getPlanId(), snapshot.getSourcePlanRevision(),
                List.copyOf(accepted));
    }

    private ProcessingOperationType supportedOperation(String value) {
        ProcessingOperationType operation;
        try {
            operation = ProcessingOperationType.valueOf(value);
        } catch (Exception e) {
            throw unsupported();
        }
        if (!operation.isExecutable()) {
            throw unsupported(value);
        }
        return operation;
    }

    private void validateNormalization(Map<String, Object> parameters) {
        inRange(number(parameters, "targetLufs"), MIN_TARGET_LUFS,
                MAX_TARGET_LUFS, "targetLufs");
    }

    private void validateDenoiseStrength(Map<String, Object> parameters) {
        Object value = parameters.get("strength");
        if (!(value instanceof String strength)
                || !("LIGHT".equals(strength) || "MEDIUM".equals(strength)
                || "STRONG".equals(strength))) {
            throw invalidParameter("strength must be LIGHT, MEDIUM or STRONG");
        }
    }

    private void validateSilenceCleanup(Map<String, Object> parameters) {
        Object modeValue = parameters.get("mode");
        if (!(modeValue instanceof String mode)
                || !("COMPRESS".equals(mode) || "REMOVE".equals(mode))) {
            throw invalidParameter("mode must be COMPRESS or REMOVE");
        }
        BigDecimal minSilence = wholeMilliseconds(parameters,
                "minSilenceMs");
        inRange(minSilence, MIN_SILENCE_MS, MAX_SILENCE_MS,
                "minSilenceMs");
        if ("COMPRESS".equals(mode)) {
            BigDecimal keepSilence = wholeMilliseconds(parameters,
                    "keepSilenceMs");
            inRange(keepSilence, MIN_KEEP_SILENCE_MS,
                    MAX_KEEP_SILENCE_MS, "keepSilenceMs");
            if (keepSilence.compareTo(minSilence) >= 0) {
                throw invalidParameter(
                        "keepSilenceMs must be shorter than minSilenceMs");
            }
        }
    }

    private BigDecimal wholeMilliseconds(Map<String, Object> parameters,
                                         String name) {
        BigDecimal value = number(parameters, name);
        try {
            value.longValueExact();
            return value;
        } catch (ArithmeticException e) {
            throw invalidParameter(name + " must be a whole number");
        }
    }

    private void requireRange(ProcessingConfirmationVO.Step step) {
        if (step.getStartMs() == null || step.getEndMs() == null
                || step.getStartMs() < 0
                || step.getEndMs() <= step.getStartMs()) {
            throw invalidParameter("Segment startMs/endMs are invalid");
        }
    }

    private BigDecimal number(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (!(value instanceof Number number)) {
            throw invalidParameter(name + " must be a finite number");
        }
        if (number instanceof Double d && !Double.isFinite(d)
                || number instanceof Float f && !Float.isFinite(f)) {
            throw invalidParameter(name + " must be a finite number");
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            throw invalidParameter(name + " must be a finite number");
        }
    }

    private void inRange(BigDecimal value, BigDecimal minimum,
                         BigDecimal maximum, String name) {
        if (value.compareTo(minimum) < 0
                || value.compareTo(maximum) > 0) {
            throw invalidParameter(name + " is outside the safe range");
        }
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw invalidSnapshot(name + " is missing from the snapshot");
        }
    }

    private List<ProcessingConfirmationVO.Step> safe(
            List<ProcessingConfirmationVO.Step> steps) {
        return steps == null ? List.of() : steps;
    }

    private ProcessingExecutionException unsupported() {
        return unsupported(null);
    }

    private ProcessingExecutionException unsupported(String operation) {
        String detail = operation == null ? "unknown" : operation;
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION,
                false, "Confirmed plan contains legacy operation " + detail
                + "; regenerate and confirm a new processing plan");
    }

    private ProcessingExecutionException invalidSnapshot(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_CONFIRMATION_NOT_READY,
                false, message);
    }

    private ProcessingExecutionException invalidParameter(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                false, message);
    }
}
