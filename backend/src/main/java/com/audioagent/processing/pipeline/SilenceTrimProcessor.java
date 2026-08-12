package com.audioagent.processing.pipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SilenceTrimProcessor {

    private final FfmpegCommandExecutor ffmpeg;
    private final SilenceTrimPlanner planner;

    public SilenceTrimPlanner.Plan process(
            Path input, Path output, long inputDurationMs,
            List<ExecutableProcessingStep> steps) {
        SilenceTrimPlanner.Plan plan = planner.plan(inputDurationMs, steps);
        List<String> filters = new ArrayList<>();
        StringBuilder concatInputs = new StringBuilder();
        for (int index = 0; index < plan.retained().size(); index++) {
            SilenceTrimPlanner.Range range = plan.retained().get(index);
            String label = "keep" + index;
            filters.add("[0:a]atrim=start="
                    + FfmpegValueFormatter.seconds(range.startMs())
                    + ":end="
                    + FfmpegValueFormatter.seconds(range.endMs())
                    + ",asetpts=PTS-STARTPTS[" + label + "]");
            concatInputs.append('[').append(label).append(']');
        }
        filters.add(concatInputs + "concat=n=" + plan.retained().size()
                + ":v=0:a=1[outa]");
        ffmpeg.transform(input, output, null,
                String.join(";", filters), "[outa]", "TRIM_SEGMENT");
        return plan;
    }
}
