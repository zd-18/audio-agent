package com.audioagent.processing.pipeline;

import com.audioagent.processing.exception.ProcessingExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoudnormOutputParserTest {

    private LoudnormOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new LoudnormOutputParser(new ObjectMapper());
    }

    @Test
    void parsesFirstPassMeasurementsFromFfmpegOutput() {
        LoudnormMeasurement result = parser.parse("prefix\n{\n"
                + "\"input_i\": \"-20.10\",\n"
                + "\"input_tp\": \"-2.20\",\n"
                + "\"input_lra\": \"5.30\",\n"
                + "\"input_thresh\": \"-30.00\",\n"
                + "\"target_offset\": \"0.10\"\n}\nsuffix");
        assertEquals(new BigDecimal("-20.10"), result.inputI());
        assertEquals(new BigDecimal("0.10"), result.targetOffset());
    }

    @Test
    void missingJsonIsRetryableFailure() {
        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> parser.parse("not-json"));
        assertTrue(error.isRetryable());
    }

    @Test
    void missingMeasurementIsRejected() {
        assertThrows(ProcessingExecutionException.class,
                () -> parser.parse("{\"input_i\":\"-20\"}"));
    }

    @Test
    void infiniteMeasurementIsRejectedWithoutFabrication() {
        String json = "{\"input_i\":\"-inf\","
                + "\"input_tp\":\"-2\",\"input_lra\":\"5\","
                + "\"input_thresh\":\"-30\","
                + "\"target_offset\":\"0\"}";
        assertThrows(ProcessingExecutionException.class,
                () -> parser.parse(json));
    }
}
