package com.audioagent.file.service;

import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioVersionSummaryBuilderTest {

    private final AudioVersionSummaryBuilder builder =
            new AudioVersionSummaryBuilder(new ObjectMapper());

    @Test
    void silenceCleanupCompressSummarizesAsCompressedLongSilence() {
        assertEquals("压缩长静音", builder.build(List.of(
                step(1, "SILENCE_CLEANUP",
                        "{\"mode\":\"COMPRESS\",\"minSilenceMs\":3000}"))));
    }

    @Test
    void silenceCleanupRemoveSummarizesAsDeletedLongSilence() {
        assertEquals("删除长静音", builder.build(List.of(
                step(1, "SILENCE_CLEANUP",
                        "{\"mode\":\"REMOVE\",\"minSilenceMs\":3000}"))));
    }

    @Test
    void silenceCleanupWithoutModeDefaultsToCompress() {
        assertEquals("压缩长静音", builder.build(List.of(
                step(1, "SILENCE_CLEANUP",
                        "{\"minSilenceMs\":3000}"))));
        assertEquals("压缩长静音", builder.build(List.of(
                step(1, "SILENCE_CLEANUP", null))));
    }

    @Test
    void combinesSilenceCleanupWithOtherOperationsInStepOrder() {
        List<AudioProcessingExecutionStep> steps = List.of(
                step(1, "TRIM_SEGMENT", null),
                step(2, "SILENCE_CLEANUP",
                        "{\"mode\":\"REMOVE\"}"),
                step(3, "DENOISE", null));

        assertEquals("裁剪片段、删除长静音、智能降噪",
                builder.build(steps));
    }

    @Test
    void ignoresMalformedParametersJson() {
        assertEquals("压缩长静音", builder.build(List.of(
                step(1, "SILENCE_CLEANUP", "{not-json}"))));
    }

    private AudioProcessingExecutionStep step(int order,
                                              String operationType,
                                              String parametersJson) {
        AudioProcessingExecutionStep step = new AudioProcessingExecutionStep();
        step.setStepOrder(order);
        step.setOperationType(operationType);
        step.setEffectiveParametersJson(parametersJson);
        return step;
    }
}
