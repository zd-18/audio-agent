package com.audioagent.analysis.processing;

import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProcessingParameterValidator {

    private static final BigDecimal MIN_TARGET_LUFS = BigDecimal.valueOf(-24);
    private static final BigDecimal MAX_TARGET_LUFS = BigDecimal.valueOf(-8);
    private static final BigDecimal MIN_TRUE_PEAK_DBFS = BigDecimal.valueOf(-6);
    private static final BigDecimal MAX_TRUE_PEAK_DBFS = BigDecimal.ZERO;

    private final AnalysisProperties properties;

    public Map<String, Object> mergeAndValidate(
            AudioProcessingStep step, Map<String, Object> original,
            Map<String, Object> overrides) {
        ProcessingOperationType operation = operation(step);
        Map<String, Object> safeOriginal = original == null
                ? Map.of() : original;
        Map<String, Object> safeOverrides = overrides == null
                ? Map.of() : overrides;
        Set<String> allowed = allowed(operation);
        for (Map.Entry<String, Object> entry : safeOverrides.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue() == null) {
                throw invalid("Parameter is not editable: " + entry.getKey());
            }
        }
        Map<String, Object> effective = new LinkedHashMap<>(safeOriginal);
        effective.putAll(safeOverrides);
        validateEffective(operation, step, effective);
        return Map.copyOf(effective);
    }

    private Set<String> allowed(ProcessingOperationType operation) {
        return switch (operation) {
            case TRIM_SEGMENT -> Set.of();
            case NORMALIZE_VOLUME -> Set.of(
                    "targetLufs", "truePeakLimitDbfs");
            case DENOISE -> Set.of("strength");
            case TRIM_SILENCE -> Set.of(
                    "suggestedKeepHeadMs", "suggestedKeepTailMs");
            case INCREASE_GAIN, DECREASE_GAIN -> Set.of("suggestedGainDb");
            case DENOISE_REVIEW -> Set.of("suggestedStrength");
            case NORMALIZE_LOUDNESS -> Set.of(
                    "targetLufs", "truePeakLimitDbfs");
            case LIMIT_PEAK -> Set.of("truePeakLimitDbfs");
            case REVIEW_SILENCE -> Set.of();
            default -> throw unsupported(operation);
        };
    }

    private void validateEffective(ProcessingOperationType operation,
                                   AudioProcessingStep step,
                                   Map<String, Object> parameters) {
        switch (operation) {
            case TRIM_SEGMENT -> validateSegmentRange(step);
            case NORMALIZE_VOLUME -> validateNormalization(parameters);
            case DENOISE -> validateDenoiseStrength(parameters);
            case TRIM_SILENCE -> validateTrim(step, parameters);
            case INCREASE_GAIN -> validateGain(parameters, true);
            case DECREASE_GAIN -> validateGain(parameters, false);
            case DENOISE_REVIEW -> validateDenoise(parameters);
            case NORMALIZE_LOUDNESS -> validateNormalization(parameters);
            case LIMIT_PEAK -> inRange(
                    number(parameters, "truePeakLimitDbfs"),
                    MIN_TRUE_PEAK_DBFS, MAX_TRUE_PEAK_DBFS,
                    "truePeakLimitDbfs");
            case REVIEW_SILENCE -> {
                // This operation intentionally has no editable parameters.
            }
            default -> throw unsupported(operation);
        }
    }

    private void validateSegmentRange(AudioProcessingStep step) {
        if (step.getStartMs() == null || step.getEndMs() == null
                || step.getStartMs() < 0
                || step.getEndMs() <= step.getStartMs()) {
            throw invalid("Segment startMs/endMs are invalid");
        }
    }

    private void validateNormalization(Map<String, Object> parameters) {
        inRange(number(parameters, "targetLufs"),
                MIN_TARGET_LUFS, MAX_TARGET_LUFS, "targetLufs");
        inRange(number(parameters, "truePeakLimitDbfs"),
                MIN_TRUE_PEAK_DBFS, MAX_TRUE_PEAK_DBFS,
                "truePeakLimitDbfs");
    }

    private void validateTrim(AudioProcessingStep step,
                              Map<String, Object> parameters) {
        BigDecimal head = number(parameters, "suggestedKeepHeadMs");
        BigDecimal tail = number(parameters, "suggestedKeepTailMs");
        if (head.signum() < 0 || tail.signum() < 0) {
            throw invalid("Silence keep durations cannot be negative");
        }
        if (step.getStartMs() == null || step.getEndMs() == null
                || step.getEndMs() < step.getStartMs()
                || head.add(tail).compareTo(BigDecimal.valueOf(
                step.getEndMs() - step.getStartMs())) >= 0) {
            throw invalid("Silence keep durations must be shorter than the segment");
        }
    }

    private void validateGain(Map<String, Object> parameters,
                              boolean increase) {
        BigDecimal gain = number(parameters, "suggestedGainDb");
        if ((increase && gain.signum() <= 0)
                || (!increase && gain.signum() >= 0)) {
            throw invalid(increase
                    ? "Increase gain must be greater than 0"
                    : "Decrease gain must be less than 0");
        }
        BigDecimal maximum = properties.getProcessingPlan().getGain()
                .getMaxAbsoluteDb();
        if (gain.abs().compareTo(maximum) > 0) {
            throw invalid("Gain exceeds configured maxAbsoluteDb");
        }
    }

    private void validateDenoise(Map<String, Object> parameters) {
        Object value = parameters.get("suggestedStrength");
        if (!(value instanceof String strength)
                || !("LIGHT".equals(strength) || "MEDIUM".equals(strength))) {
            throw invalid("suggestedStrength must be LIGHT or MEDIUM");
        }
    }

    private void validateDenoiseStrength(Map<String, Object> parameters) {
        Object value = parameters.get("strength");
        if (!(value instanceof String strength)
                || !("LIGHT".equals(strength) || "MEDIUM".equals(strength)
                || "STRONG".equals(strength))) {
            throw invalid("strength must be LIGHT, MEDIUM or STRONG");
        }
    }

    private BigDecimal number(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (!(value instanceof Number number)) {
            throw invalid(name + " must be a finite number");
        }
        if (number instanceof Double d && !Double.isFinite(d)
                || number instanceof Float f && !Float.isFinite(f)) {
            throw invalid(name + " must be a finite number");
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            throw invalid(name + " must be a finite number");
        }
    }

    private void inRange(BigDecimal value, BigDecimal minimum,
                         BigDecimal maximum, String name) {
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw invalid(name + " must be between " + minimum
                    + " and " + maximum);
        }
    }

    private ProcessingOperationType operation(AudioProcessingStep step) {
        try {
            return ProcessingOperationType.valueOf(step.getOperationType());
        } catch (Exception e) {
            throw invalid("Unsupported processing operation");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PROCESSING_PARAMETER_INVALID,
                message);
    }

    private BusinessException unsupported(
            ProcessingOperationType operation) {
        return invalid("Unsupported processing operation: " + operation);
    }
}
