package com.audioagent.agent.workflow.planner;

import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.analysis.entity.AudioProcessingStep;
import com.audioagent.analysis.processing.ProcessingOperationCatalog;
import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.analysis.processing.ProcessingParameterValidator;
import com.audioagent.analysis.processing.ProcessingPlanDraft;
import com.audioagent.analysis.processing.ProcessingPlanStatus;
import com.audioagent.analysis.processing.ProcessingPriority;
import com.audioagent.analysis.processing.ProcessingRiskLevel;
import com.audioagent.analysis.processing.ProcessingStepDraft;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AgentProcessingPlanParser {

    private static final Set<String> ROOT_FIELDS = Set.of("summary", "steps");
    private static final Set<String> STEP_FIELDS = Set.of(
            "order", "operationType", "parameters", "startMs", "endMs",
            "reason");
    private static final int MAX_STEPS = 10;

    private final ObjectMapper objectMapper;
    private final ProcessingParameterValidator parameterValidator;
    private final ProcessingOperationCatalog operationCatalog;

    public ProcessingPlanDraft parse(String raw, long audioDurationMs) {
        if (raw == null || raw.isBlank() || audioDurationMs <= 0) {
            throw invalid("Planner returned no usable plan", null);
        }
        try {
            JsonNode root = objectMapper.readTree(stripFence(raw.trim()));
            requireObject(root, "Planner response must be a JSON object");
            requireOnly(root, ROOT_FIELDS, "Planner response");
            String summary = text(root, "summary", 500);
            JsonNode rawSteps = root.get("steps");
            if (rawSteps == null || !rawSteps.isArray()
                    || rawSteps.isEmpty() || rawSteps.size() > MAX_STEPS) {
                throw invalid("Planner steps must contain 1 to " + MAX_STEPS
                        + " items", null);
            }

            List<ProcessingStepDraft> steps = new ArrayList<>();
            List<Range> trimRanges = new ArrayList<>();
            boolean hasNormalization = false;
            for (int index = 0; index < rawSteps.size(); index++) {
                JsonNode node = rawSteps.get(index);
                requireObject(node, "Each Planner step must be an object");
                requireOnly(node, STEP_FIELDS, "Planner step");
                int order = integer(node, "order");
                if (order != index + 1) {
                    throw invalid("Planner step order must be continuous", null);
                }
                ProcessingOperationType operation = operation(node);
                Map<String, Object> parameters = parameters(node);
                Long startMs = nullableLong(node, "startMs");
                Long endMs = nullableLong(node, "endMs");
                String reason = text(node, "reason", 500);

                if (operation == ProcessingOperationType.NORMALIZE_VOLUME) {
                    if (hasNormalization || startMs != null || endMs != null) {
                        throw invalid("NORMALIZE_VOLUME must be a single whole-audio step", null);
                    }
                    hasNormalization = true;
                } else {
                    if (!parameters.isEmpty() || startMs == null || endMs == null
                            || startMs < 0 || endMs <= startMs
                            || endMs > audioDurationMs) {
                        throw invalid("TRIM_SEGMENT range is outside the audio duration", null);
                    }
                    trimRanges.add(new Range(startMs, endMs));
                }

                validateParameters(operation, startMs, endMs, parameters);
                steps.add(new ProcessingStepDraft(operation,
                        operationCatalog.title(operation),
                        operationCatalog.description(operation, startMs, endMs),
                        null, startMs, endMs, ProcessingPriority.MEDIUM,
                        operation == ProcessingOperationType.TRIM_SEGMENT
                                ? ProcessingRiskLevel.HIGH
                                : ProcessingRiskLevel.MEDIUM,
                        true, parameters, reason));
            }
            validateNonOverlapping(trimRanges);
            long removedMs = trimRanges.stream()
                    .mapToLong(range -> range.endMs() - range.startMs()).sum();
            long estimatedDurationMs = audioDurationMs - removedMs;
            if (estimatedDurationMs <= 0) {
                throw invalid("The plan would remove the entire audio file", null);
            }
            return new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                    summary, estimatedDurationMs, List.copyOf(steps), 0,
                    trimRanges.size());
        } catch (AgentExecutionException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw invalid("Planner response is not valid JSON", e);
        }
    }

    private void validateParameters(ProcessingOperationType operation,
                                    Long startMs, Long endMs,
                                    Map<String, Object> parameters) {
        AudioProcessingStep step = new AudioProcessingStep();
        step.setOperationType(operation.name());
        step.setStartMs(startMs);
        step.setEndMs(endMs);
        try {
            parameterValidator.mergeAndValidate(step, parameters, Map.of());
        } catch (BusinessException e) {
            throw invalid("Planner parameters are invalid: " + e.getMessage(), e);
        }
    }

    private void validateNonOverlapping(List<Range> ranges) {
        List<Range> ordered = ranges.stream()
                .sorted(Comparator.comparingLong(Range::startMs)).toList();
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).startMs() < ordered.get(i - 1).endMs()) {
                throw invalid("TRIM_SEGMENT ranges must not overlap", null);
            }
        }
    }

    private ProcessingOperationType operation(JsonNode node) {
        String value = text(node, "operationType", 32);
        try {
            ProcessingOperationType operation = ProcessingOperationType.valueOf(value);
            if (!operation.isExecutable()) {
                throw invalid("Unsupported operationType: " + value, null);
            }
            return operation;
        } catch (IllegalArgumentException e) {
            throw invalid("Unsupported operationType: " + value, e);
        }
    }

    private Map<String, Object> parameters(JsonNode node)
            throws JsonProcessingException {
        JsonNode value = node.get("parameters");
        if (value == null || !value.isObject()) {
            throw invalid("Step parameters must be an object", null);
        }
        try {
            return objectMapper.convertValue(value,
                    new TypeReference<>() { });
        } catch (IllegalArgumentException e) {
            throw invalid("Step parameters cannot be parsed", e);
        }
    }

    private void requireOnly(JsonNode node, Set<String> allowed,
                             String label) {
        Set<String> unexpected = new HashSet<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                unexpected.add(field);
            }
        }
        if (!unexpected.isEmpty()) {
            throw invalid(label + " contains unsupported fields: "
                    + unexpected, null);
        }
    }

    private void requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) {
            throw invalid(message, null);
        }
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()
                || value.asText().length() > maxLength) {
            throw invalid(field + " is missing or invalid", null);
        }
        return value.asText().trim();
    }

    private int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
            throw invalid(field + " must be an integer", null);
        }
        return value.intValue();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid(field + " must be an integer or null", null);
        }
        return value.longValue();
    }

    private String stripFence(String raw) {
        if (!raw.startsWith("```")) {
            return raw;
        }
        int firstNewline = raw.indexOf('\n');
        int lastFence = raw.lastIndexOf("```");
        return firstNewline >= 0 && lastFence > firstNewline
                ? raw.substring(firstNewline + 1, lastFence).trim() : raw;
    }

    private AgentExecutionException invalid(String message,
                                            Throwable cause) {
        return new AgentExecutionException(ErrorCode.AGENT_PLAN_INVALID,
                false, message, cause);
    }

    private record Range(long startMs, long endMs) { }
}
