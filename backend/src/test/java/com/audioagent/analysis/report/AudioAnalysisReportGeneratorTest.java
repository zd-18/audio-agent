package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioAnalysisReportGeneratorTest {

    private AnalysisProperties properties;
    private AudioAnalysisReportGenerator generator;

    @BeforeEach
    void setUp() {
        properties = new AnalysisProperties();
        LoudnessEvaluator evaluator = new LoudnessEvaluator(properties);
        generator = new AudioAnalysisReportGenerator(properties,
                new AudioQualityScoreCalculator(properties, evaluator),
                new ReportSummaryBuilder(),
                new AudioRecommendationBuilder(properties, evaluator),
                evaluator);
    }

    @Test
    void timelineIsChronologicalAndKeyIssuesUseProductOrderingAndLimit() {
        properties.getReport().setMaxKeyIssues(2);
        AudioAnalysisReportGenerator.GeneratedReport report = generator
                .generate(file(), result(), List.of(
                        issue(1, "SILENCE", "LOW", 5000, 7000),
                        issue(2, "VOLUME_DROP", "HIGH", 3000, 4000),
                        issue(3, "NOISE_RISK", "HIGH", 1000, 2500)));
        assertEquals(List.of(3L, 2L, 1L), report.payload().getTimeline()
                .stream().map(AudioAnalysisReportVO.ReportIssue::getIssueId)
                .toList());
        assertEquals(List.of(3L, 2L), report.payload().getKeyIssues()
                .stream().map(AudioAnalysisReportVO.ReportIssue::getIssueId)
                .toList());
        assertEquals(3, report.payload().getIssueSummary()
                .getTotalIssueCount());
    }

    @Test
    void oldResultWithoutLoudnessProducesNullOverviewAndEmptyLists() {
        AudioAnalysisReportGenerator.GeneratedReport report = generator
                .generate(file(), result(), List.of());
        assertNull(report.payload().getLoudnessOverview());
        assertTrue(report.payload().getTimeline().isEmpty());
        assertTrue(report.payload().getKeyIssues().isEmpty());
        assertEquals(0, report.payload().getIssueSummary()
                .getTotalIssueCount());
    }

    @Test
    void longIdsSerializeAsStringsWithoutPrecisionLoss() throws Exception {
        AudioAnalysisReportVO vo = AudioAnalysisReportVO.builder()
                .reportId(9223372036854775806L)
                .taskId(9223372036854775805L)
                .audioFileId(9223372036854775804L)
                .build();
        String json = new ObjectMapper().writeValueAsString(vo);
        assertTrue(json.contains("\"9223372036854775806\""));
        assertFalse(json.contains("9223372036854775806,"));
    }

    private AudioFile file() {
        AudioFile file = new AudioFile();
        file.setOriginalName("demo.wav");
        return file;
    }

    private AudioAnalysisResult result() {
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setDurationMs(10000L);
        result.setFormatName("wav");
        result.setCodecName("pcm_s16le");
        result.setSampleRate(48000);
        result.setChannels(2);
        return result;
    }

    private AudioIssueSegment issue(long id, String type, String severity,
                                    long start, long end) {
        AudioIssueSegment issue = new AudioIssueSegment();
        issue.setId(id);
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setStartMs(start);
        issue.setEndMs(end);
        issue.setDurationMs(end - start);
        return issue;
    }
}
