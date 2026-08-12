package com.audioagent.processing.pipeline;

import com.audioagent.processing.config.AudioProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class LoudnessNormalizeProcessor {

    private final FfmpegCommandExecutor ffmpeg;
    private final LoudnormOutputParser parser;
    private final AudioProcessingProperties properties;

    public void process(Path input, Path output,
                        ExecutableProcessingStep step) {
        BigDecimal targetI = number(step, "targetLufs");
        BigDecimal targetTp = number(step, "truePeakLimitDbfs");
        BigDecimal targetLra = properties.getLoudness().getTargetLra();
        String targets = "I=" + FfmpegValueFormatter.decimal(targetI)
                + ":TP=" + FfmpegValueFormatter.decimal(targetTp)
                + ":LRA=" + FfmpegValueFormatter.decimal(targetLra);
        String analysisFilter = "loudnorm=" + targets
                + ":print_format=json";
        LoudnormMeasurement measured = parser.parse(
                ffmpeg.analyzeLoudness(input, analysisFilter));
        String secondPass = "loudnorm=" + targets
                + ":measured_I=" + value(measured.inputI())
                + ":measured_TP=" + value(measured.inputTp())
                + ":measured_LRA=" + value(measured.inputLra())
                + ":measured_thresh=" + value(measured.inputThresh())
                + ":offset=" + value(measured.targetOffset())
                + ":linear=true:print_format=summary";
        ffmpeg.transform(input, output, secondPass, null, null,
                "NORMALIZE_VOLUME");
    }

    private BigDecimal number(ExecutableProcessingStep step, String name) {
        return new BigDecimal(((Number) step.parameters().get(name))
                .toString());
    }

    private String value(BigDecimal value) {
        return FfmpegValueFormatter.decimal(value);
    }
}
