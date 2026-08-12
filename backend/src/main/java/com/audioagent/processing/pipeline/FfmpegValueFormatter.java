package com.audioagent.processing.pipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FfmpegValueFormatter {

    private FfmpegValueFormatter() {
    }

    public static String seconds(long milliseconds) {
        return BigDecimal.valueOf(milliseconds, 3)
                .stripTrailingZeros().toPlainString();
    }

    public static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static String limiterAmplitude(BigDecimal dbfs) {
        double amplitude = Math.pow(10.0, dbfs.doubleValue() / 20.0);
        return BigDecimal.valueOf(amplitude)
                .setScale(8, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
