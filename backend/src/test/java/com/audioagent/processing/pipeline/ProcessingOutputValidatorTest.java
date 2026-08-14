package com.audioagent.processing.pipeline;

import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.exception.ProcessingExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingOutputValidatorTest {

    private ProcessingOutputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProcessingOutputValidator(
                new AudioProcessingProperties());
    }

    @Test
    void durationFailureUsesSafeUserMessageWithoutDiagnostics() {
        AudioMetadata metadata = AudioMetadata.builder()
                .durationMs(12_500L)
                .sampleRate(48_000)
                .channels(2)
                .build();

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> validator.validateMetadata(metadata, 10_000L));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_OUTPUT_INVALID.name(),
                error.getFailureCode());
        assertEquals("处理后的音频时长异常，请重新处理或检查源文件。",
                error.getMessage());
        assertFalse(error.getMessage().contains("expectedDurationMs"));
    }

    @Test
    void decodedDurationDifferencePassesAtExistingTolerance() {
        AudioMetadata metadata = AudioMetadata.builder()
                .durationMs(76_558L)
                .sampleRate(44_100)
                .channels(2)
                .build();

        validator.validateMetadata(metadata, 76_409L);

        assertEquals(1_000L, validator.durationToleranceMs());
    }
}
