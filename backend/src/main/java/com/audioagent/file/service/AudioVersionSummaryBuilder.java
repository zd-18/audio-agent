package com.audioagent.file.service;

import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioVersionSummaryBuilder {

    private final ObjectMapper objectMapper;

    public String build(List<AudioProcessingExecutionStep> steps) {
        LinkedHashSet<String> summaries = new LinkedHashSet<>();
        if (steps != null) {
            steps.stream()
                    .filter(step -> step != null
                            && step.getOperationType() != null)
                    .sorted(Comparator.comparing(
                            AudioProcessingExecutionStep::getStepOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .map(this::summary)
                    .forEach(summaries::add);
        }
        return summaries.isEmpty()
                ? "音频优化" : String.join("、", summaries);
    }

    private String summary(AudioProcessingExecutionStep step) {
        return switch (step.getOperationType()) {
            case "NORMALIZE_VOLUME" -> "音量优化";
            case "TRIM_SEGMENT" -> "裁剪片段";
            case "DENOISE" -> "智能降噪";
            case "SILENCE_CLEANUP" -> "REMOVE".equals(mode(step))
                    ? "删除长静音" : "压缩长静音";
            default -> "音频优化";
        };
    }

    private String mode(AudioProcessingExecutionStep step) {
        String json = step.getEffectiveParametersJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode mode = root.get("mode");
            return mode == null || !mode.isTextual()
                    ? null : mode.asText();
        } catch (Exception e) {
            log.debug("Could not parse effective parameters for step "
                    + "operationType={}, executionStepId={}",
                    step.getOperationType(), step.getId(), e);
            return null;
        }
    }
}
