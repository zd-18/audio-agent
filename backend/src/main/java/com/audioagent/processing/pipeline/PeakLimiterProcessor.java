package com.audioagent.processing.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class PeakLimiterProcessor {

    private final FfmpegCommandExecutor ffmpeg;

    public void process(Path input, Path output,
                        ExecutableProcessingStep step) {
        BigDecimal dbfs = new BigDecimal(((Number) step.parameters()
                .get("truePeakLimitDbfs")).toString());
        String amplitude = FfmpegValueFormatter.limiterAmplitude(dbfs);
        ffmpeg.transform(input, output,
                "alimiter=limit=" + amplitude, null, null,
                "LIMIT_PEAK");
    }
}
