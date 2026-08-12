package com.audioagent.analysis.vo;

import com.audioagent.analysis.entity.AudioIssueSegment;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Data
@Builder
@Slf4j
public class IssueSegmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long issueId;

    private String issueType;

    private Long startMs;

    private Long endMs;

    private Long durationMs;

    private String severity;

    private String description;

    private Map<String, Object> metrics;

    public static IssueSegmentVO from(AudioIssueSegment segment,
                                      ObjectMapper objectMapper) {
        return IssueSegmentVO.builder()
                .issueId(segment.getId())
                .issueType(segment.getIssueType())
                .startMs(segment.getStartMs())
                .endMs(segment.getEndMs())
                .durationMs(segment.getDurationMs())
                .severity(segment.getSeverity())
                .description(segment.getDescription())
                .metrics(parseMetrics(segment, objectMapper))
                .build();
    }

    private static Map<String, Object> parseMetrics(
            AudioIssueSegment segment,
            ObjectMapper objectMapper
    ) {
        if (segment.getMetricJson() == null
                || segment.getMetricJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(segment.getMetricJson(),
                    new TypeReference<>() {
                    });
        } catch (Exception e) {
            log.warn("Unable to parse issue metrics, issueId={}",
                    segment.getId());
            return Map.of();
        }
    }
}
