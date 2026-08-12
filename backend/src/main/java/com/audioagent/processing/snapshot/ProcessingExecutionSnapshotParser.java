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
    private static final BigDecimal MIN_TRUE_PEAK_DBFS =
            BigDecimal.valueOf(-6);
    private static final BigDecimal MAX_TRUE_PEAK_DBFS = BigDecimal.ZERO;

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
        int normalizeCount = 0;
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
            if (operation == ProcessingOperationType.TRIM_SEGMENT) {
                requireRange(step);
            } else {
                validateNormalization(parameters);
                normalizeCount += 1;
                if (normalizeCount > 1) {
                    throw invalidParameter(
                            "NORMALIZE_VOLUME may appear at most once");
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
        if (operation != ProcessingOperationType.NORMALIZE_VOLUME
                && operation != ProcessingOperationType.TRIM_SEGMENT) {
            throw unsupported();
        }
        return operation;
    }

    private void validateNormalization(Map<String, Object> parameters) {
        inRange(number(parameters, "targetLufs"), MIN_TARGET_LUFS,
                MAX_TARGET_LUFS, "targetLufs");
        inRange(number(parameters, "truePeakLimitDbfs"),
                MIN_TRUE_PEAK_DBFS, MAX_TRUE_PEAK_DBFS,
                "truePeakLimitDbfs");
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
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_UNSUPPORTED_OPERATION,
                false, "Only NORMALIZE_VOLUME and TRIM_SEGMENT are supported");
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
