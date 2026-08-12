package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.Recommendation;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportBuildersTest {

    private AnalysisProperties properties;
    private ReportSummaryBuilder summaryBuilder;
    private AudioRecommendationBuilder recommendationBuilder;

    @BeforeEach
    void setUp() {
        properties = new AnalysisProperties();
        LoudnessEvaluator evaluator = new LoudnessEvaluator(properties);
        summaryBuilder = new ReportSummaryBuilder();
        recommendationBuilder = new AudioRecommendationBuilder(properties,
                evaluator);
    }

    @Test
    void summaryReflectsNoProblemsAndMultipleRealProblemTypes() {
        assertEquals("本段音频整体质量良好，未发现明显的静音、音量波动或背景噪声风险。",
                summaryBuilder.build(List.of()));
        String summary = summaryBuilder.build(List.of(
                issue(1, "SILENCE", "HIGH", 0),
                issue(2, "NOISE_RISK", "LOW", 2000)));
        assertTrue(summary.contains("静音"));
        assertTrue(summary.contains("噪声风险"));
        assertTrue(summary.contains("高严重程度"));
    }

    @Test
    void recommendationsAreDeduplicatedPrioritizedAndLimited() {
        properties.getReport().setMaxRecommendations(2);
        List<Recommendation> recommendations = recommendationBuilder.build(
                new AudioAnalysisResult(), List.of(
                        issue(1, "SILENCE", "LOW", 0),
                        issue(2, "SILENCE", "HIGH", 2000),
                        issue(3, "NOISE_RISK", "MEDIUM", 4000)));
        assertEquals(2, recommendations.size());
        assertEquals("HIGH", recommendations.get(0).getPriority());
        assertEquals(2L, recommendations.get(0).getIssueId());
        assertEquals("MEDIUM", recommendations.get(1).getPriority());
    }

    private AudioIssueSegment issue(long id, String type, String severity,
                                    long start) {
        AudioIssueSegment issue = new AudioIssueSegment();
        issue.setId(id);
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setStartMs(start);
        issue.setEndMs(start + 1000);
        issue.setDurationMs(1000L);
        return issue;
    }
}
