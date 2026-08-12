package com.audioagent.contentanalysis.model;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisTypeCodec {

    private final ObjectMapper objectMapper;

    public String write(Set<AnalysisType> types) {
        try {
            return objectMapper.writeValueAsString(normalize(types));
        } catch (JsonProcessingException e) {
            throw invalid(e);
        }
    }

    public List<AnalysisType> read(String json) {
        return read(json, null);
    }

    public List<AnalysisType> read(String json, Long taskId) {
        if (!StringUtils.hasText(json)) {
            log.warn("Content analysis task field is missing, "
                            + "taskId={}, field=analysis_types, fieldState={}",
                    taskId, json == null ? "NULL" : "BLANK");
            throw invalid(null);
        }
        try {
            List<AnalysisType> values = objectMapper.readValue(
                    json, new TypeReference<>() {
                    });
            return normalize(values == null
                    ? Set.of() : Set.copyOf(values));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Content analysis task field is invalid, "
                            + "taskId={}, field=analysis_types, "
                            + "fieldState=INVALID_JSON",
                    taskId);
            throw invalid(e);
        }
    }

    public List<AnalysisType> normalize(Set<AnalysisType> types) {
        Set<AnalysisType> normalized = types == null || types.isEmpty()
                ? EnumSet.allOf(AnalysisType.class)
                : EnumSet.copyOf(types);
        return normalized.stream()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    private ContentAnalysisException invalid(Throwable cause) {
        return new ContentAnalysisException(
                ErrorCode.AI_TASK_DATA_INVALID, false,
                "智能分析任务数据不完整", cause);
    }
}
