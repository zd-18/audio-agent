package com.audioagent.processing.pipeline;

import com.audioagent.processing.config.AudioProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 * Whole-audio background noise reduction. The user-facing strength
 * (LIGHT / MEDIUM / STRONG) maps to a single afftdn noise floor; the
 * same filter style as {@link SegmentAudioProcessor} is used so both
 * local and whole-audio denoise stay consistent.
 */
@Component
@RequiredArgsConstructor
public class DenoiseProcessor {

    private final FfmpegCommandExecutor ffmpeg;
    private final AudioProcessingProperties properties;

    public void process(Path input, Path output,
                        ExecutableProcessingStep step) {
        String strength = String.valueOf(
                step.parameters().get("strength"));
        BigDecimal floor = switch (strength) {
            case "STRONG" -> properties.getDenoise()
                    .getStrongNoiseFloorDb();
            case "MEDIUM" -> properties.getDenoise()
                    .getMediumNoiseFloorDb();
            default -> properties.getDenoise()
                    .getLightNoiseFloorDb();
        };
        String filter = "afftdn=nf=" + FfmpegValueFormatter.decimal(floor);
        ffmpeg.transform(input, output, filter, null, null,
                "DENOISE");
    }
}
