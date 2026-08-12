package com.audioagent.analysis.processing;

import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingParameterValidatorTest {

    private ProcessingParameterValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProcessingParameterValidator(new AnalysisProperties());
    }

    @Test
    void mergesValidGainOverrideWithoutDroppingSystemParameters() {
        Map<String, Object> result = validator.mergeAndValidate(
                step("INCREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", 3.0, "mode", "SUGGESTION_ONLY"),
                Map.of("suggestedGainDb", 2.5));

        assertEquals(2.5, result.get("suggestedGainDb"));
        assertEquals("SUGGESTION_ONLY", result.get("mode"));
    }

    @Test
    void rejectsUnknownOverrideField() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("INCREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", 3.0),
                Map.of("startMs", 100)));
    }

    @Test
    void rejectsNonPositiveIncreaseGain() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("INCREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", 3.0),
                Map.of("suggestedGainDb", 0)));
    }

    @Test
    void rejectsIncreaseGainAboveConfiguredMaximum() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("INCREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", 3.0),
                Map.of("suggestedGainDb", 6.1)));
    }

    @Test
    void rejectsNonNegativeDecreaseGain() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("DECREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", -3.0),
                Map.of("suggestedGainDb", 1)));
    }

    @Test
    void acceptsZeroTrimKeepDuration() {
        Map<String, Object> result = validator.mergeAndValidate(
                step("TRIM_SILENCE", 1000L, 2000L),
                Map.of("suggestedKeepHeadMs", 200,
                        "suggestedKeepTailMs", 200),
                Map.of("suggestedKeepHeadMs", 0,
                        "suggestedKeepTailMs", 100));

        assertEquals(0, result.get("suggestedKeepHeadMs"));
    }

    @Test
    void rejectsTrimKeepDurationEqualToSegmentLength() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("TRIM_SILENCE", 1000L, 2000L),
                Map.of("suggestedKeepHeadMs", 200,
                        "suggestedKeepTailMs", 200),
                Map.of("suggestedKeepHeadMs", 500,
                        "suggestedKeepTailMs", 500)));
    }

    @Test
    void rejectsHighDenoiseStrength() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("DENOISE_REVIEW", 0L, 1000L),
                Map.of("suggestedStrength", "LIGHT"),
                Map.of("suggestedStrength", "HIGH")));
    }

    @Test
    void acceptsMediumDenoiseStrength() {
        Map<String, Object> result = validator.mergeAndValidate(
                step("DENOISE_REVIEW", 0L, 1000L),
                Map.of("suggestedStrength", "LIGHT"),
                Map.of("suggestedStrength", "MEDIUM"));

        assertEquals("MEDIUM", result.get("suggestedStrength"));
    }

    @Test
    void rejectsLoudnessTargetOutsideSafeRange() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("NORMALIZE_LOUDNESS", null, null),
                Map.of("targetLufs", -16.0, "truePeakLimitDbfs", -1.0),
                Map.of("targetLufs", -7.9)));
    }

    @Test
    void acceptsLoudnessBoundaryValues() {
        Map<String, Object> result = validator.mergeAndValidate(
                step("NORMALIZE_LOUDNESS", null, null),
                Map.of("targetLufs", -16.0, "truePeakLimitDbfs", -1.0),
                Map.of("targetLufs", -24, "truePeakLimitDbfs", 0));

        assertEquals(-24, result.get("targetLufs"));
        assertEquals(0, result.get("truePeakLimitDbfs"));
    }

    @Test
    void rejectsPeakLimitOutsideSafeRange() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("LIMIT_PEAK", null, null),
                Map.of("truePeakLimitDbfs", -1.0),
                Map.of("truePeakLimitDbfs", -6.1)));
    }

    @Test
    void reviewSilenceHasNoEditableParameters() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("REVIEW_SILENCE", 0L, 1000L),
                Map.of("mode", "REVIEW_BEFORE_APPLY"),
                Map.of("mode", "APPLY")));
    }

    @Test
    void rejectsNaNGain() {
        assertParameterInvalid(() -> validator.mergeAndValidate(
                step("INCREASE_GAIN", 0L, 1000L),
                Map.of("suggestedGainDb", 3.0),
                Map.of("suggestedGainDb", Double.NaN)));
    }

    private AudioProcessingStep step(String operation, Long start, Long end) {
        AudioProcessingStep step = new AudioProcessingStep();
        step.setOperationType(operation);
        step.setStartMs(start);
        step.setEndMs(end);
        return step;
    }

    private void assertParameterInvalid(Runnable action) {
        BusinessException exception = assertThrows(
                BusinessException.class, action::run);
        assertEquals(ErrorCode.PROCESSING_PARAMETER_INVALID.getCode(),
                exception.getCode());
    }
}

