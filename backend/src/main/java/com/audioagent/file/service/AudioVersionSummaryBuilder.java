package com.audioagent.file.service;

import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class AudioVersionSummaryBuilder {

    public String build(List<AudioProcessingExecutionStep> steps) {
        LinkedHashSet<String> summaries = new LinkedHashSet<>();
        if (steps != null) {
            steps.stream()
                    .filter(step -> step != null
                            && step.getOperationType() != null)
                    .sorted(Comparator.comparing(
                            AudioProcessingExecutionStep::getStepOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .map(step -> summary(step.getOperationType()))
                    .forEach(summaries::add);
        }
        return summaries.isEmpty()
                ? "音频优化" : String.join("、", summaries);
    }

    private String summary(String operationType) {
        return switch (operationType) {
            case "NORMALIZE_VOLUME" -> "音量优化";
            case "TRIM_SEGMENT" -> "裁剪片段";
            default -> "音频优化";
        };
    }
}
