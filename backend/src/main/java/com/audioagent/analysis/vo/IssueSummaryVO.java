package com.audioagent.analysis.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class IssueSummaryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;

    private Integer issueCount;

    private Long totalIssueDurationMs;

    private Integer volumeIssueCount;

    private Integer volumeDropCount;

    private Integer volumeSpikeCount;

    private Long totalVolumeIssueDurationMs;

    private Integer noiseRiskCount;

    private Long totalNoiseRiskDurationMs;

    private List<IssueSegmentVO> records;
}
