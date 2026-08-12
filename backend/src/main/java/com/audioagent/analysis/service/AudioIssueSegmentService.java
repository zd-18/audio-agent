package com.audioagent.analysis.service;

import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.analysis.noise.NoiseRiskSegment;
import com.audioagent.analysis.volume.VolumeIssueSegment;
import com.audioagent.analysis.vo.IssueSummaryVO;

import java.util.List;
import java.math.BigDecimal;

public interface AudioIssueSegmentService {

    IssueStatistics replaceSilenceSegments(Long taskId, Long audioFileId,
                                           List<SilenceSegment> segments);

    IssueSummaryVO getIssues(Long taskId, String issueType);

    VolumeIssueStatistics replaceVolumeSegments(
            Long taskId, Long audioFileId,
            List<VolumeIssueSegment> candidates,
            BigDecimal baselineIntegratedLufs,
            long audioDurationMs);

    NoiseIssueStatistics replaceNoiseRiskSegments(
            Long taskId, Long audioFileId,
            List<NoiseRiskSegment> candidates);

    record IssueStatistics(int issueCount, int silenceCount,
                           long totalSilenceDurationMs) {
    }

    record VolumeIssueStatistics(int issueCount, int volumeDropCount,
                                 int volumeSpikeCount,
                                 long totalVolumeIssueDurationMs) {
    }

    record NoiseIssueStatistics(int issueCount,
                                long totalNoiseRiskDurationMs) {
    }
}
