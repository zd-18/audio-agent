package com.audioagent.processing.pipeline;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegValueFormatterTest {

    @Test
    void millisecondsBecomePlainDecimalSeconds() {
        assertEquals("10.2", FfmpegValueFormatter.seconds(10_200));
    }

    @Test
    void subSecondValueKeepsMillisecondPrecision() {
        assertEquals("0.001", FfmpegValueFormatter.seconds(1));
    }

    @Test
    void positiveGainUsesNoScientificNotation() {
        String result = FfmpegValueFormatter.decimal(
                new BigDecimal("3.000"));
        assertEquals("3", result);
        assertFalse(result.toLowerCase().contains("e"));
    }

    @Test
    void negativeGainKeepsItsSign() {
        assertEquals("-2.5", FfmpegValueFormatter.decimal(
                new BigDecimal("-2.50")));
    }

    @Test
    void zeroDbfsLimiterIsUnity() {
        assertEquals("1", FfmpegValueFormatter.limiterAmplitude(
                BigDecimal.ZERO));
    }

    @Test
    void minusSixDbfsIsConvertedToLinearAmplitude() {
        BigDecimal result = new BigDecimal(
                FfmpegValueFormatter.limiterAmplitude(
                        BigDecimal.valueOf(-6)));
        assertTrue(result.compareTo(new BigDecimal("0.5011")) > 0);
        assertTrue(result.compareTo(new BigDecimal("0.5013")) < 0);
    }
}
