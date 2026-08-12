package com.audioagent.transcription.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.exception.TranscriptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsrResponseValidatorTest {

    private TranscriptionProperties properties;
    private AsrResponseValidator validator;

    @BeforeEach
    void setUp() {
        properties = new TranscriptionProperties();
        validator = new AsrResponseValidator(properties);
    }

    @Test
    void acceptsValidRealModelShape() {
        assertDoesNotThrow(() -> validator.validate(response()));
    }

    @Test
    void rejectsNullResponse() {
        assertInvalid(null);
    }

    @Test
    void rejectsBlankFullText() {
        AsrTranscriptionResponse response = response();
        response.setFullText(" ");
        assertInvalid(response);
    }

    @Test
    void rejectsTimestampOutsideDuration() {
        AsrTranscriptionResponse response = response();
        response.getSegments().getFirst().setEndMs(20_001L);
        assertInvalid(response);
    }

    @Test
    void rejectsConfidenceOutsideUnitInterval() {
        AsrTranscriptionResponse response = response();
        response.getSegments().getFirst()
                .setConfidence(new BigDecimal("1.01"));
        assertInvalid(response);
    }

    @Test
    void rejectsSegmentCountAboveConfiguredLimit() {
        properties.setMaxSegmentCount(1);
        AsrTranscriptionResponse response = response();
        AsrSegmentResponse second = segment(2, 5_000, 9_000,
                "第二段");
        response.setSegments(List.of(response.getSegments().getFirst(),
                second));
        assertInvalid(response);
    }

    private void assertInvalid(AsrTranscriptionResponse response) {
        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> validator.validate(response));
        assertEquals(ErrorCode.ASR_RESPONSE_INVALID,
                exception.getErrorCode());
    }

    static AsrTranscriptionResponse response() {
        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setLanguage("zh");
        response.setDurationMs(10_000L);
        response.setFullText("大家好，今天讨论项目进度。");
        response.setSegments(List.of(segment(1, 0, 4_800,
                "大家好，今天讨论项目进度。")));
        return response;
    }

    private static AsrSegmentResponse segment(int order, long start,
                                              long end, String text) {
        AsrSegmentResponse segment = new AsrSegmentResponse();
        segment.setOrder(order);
        segment.setStartMs(start);
        segment.setEndMs(end);
        segment.setText(text);
        segment.setConfidence(new BigDecimal("0.93000"));
        return segment;
    }
}
