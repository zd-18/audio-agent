package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.processing.config.AudioProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SegmentAudioProcessor {

    private final FfmpegCommandExecutor ffmpeg;
    private final AudioProcessingProperties properties;

    public void process(Path input, Path output,
                        List<ExecutableProcessingStep> steps) {
        String filters = steps.stream()
                .filter(step -> step.operationType()
                        == ProcessingOperationType.INCREASE_GAIN
                        || step.operationType()
                        == ProcessingOperationType.DECREASE_GAIN
                        || step.operationType()
                        == ProcessingOperationType.DENOISE_REVIEW)
                .sorted(Comparator.comparing(
                        ExecutableProcessingStep::startMs)
                        .thenComparing(ExecutableProcessingStep::stepOrder))
                .map(this::filter)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        ffmpeg.transform(input, output, filters, null, null,
                "LOCAL_PROCESSING");
    }

    private String filter(ExecutableProcessingStep step) {
        String enable = ":enable='between(t,"
                + FfmpegValueFormatter.seconds(step.startMs()) + ","
                + FfmpegValueFormatter.seconds(step.endMs()) + ")'";
        if (step.operationType()
                == ProcessingOperationType.DENOISE_REVIEW) {
            String strength = String.valueOf(
                    step.parameters().get("suggestedStrength"));
            BigDecimal floor = "MEDIUM".equals(strength)
                    ? properties.getDenoise().getMediumNoiseFloorDb()
                    : properties.getDenoise().getLightNoiseFloorDb();
            return "afftdn=nf=" + FfmpegValueFormatter.decimal(floor)
                    + enable;
        }
        BigDecimal gain = number(step, "suggestedGainDb");
        return "volume=" + FfmpegValueFormatter.decimal(gain) + "dB"
                + enable;
    }

    private BigDecimal number(ExecutableProcessingStep step, String name) {
        return new BigDecimal(((Number) step.parameters().get(name))
                .toString());
    }
}
