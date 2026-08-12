package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioQualityScoreCalculatorTest {

    private AnalysisProperties properties;
    private AudioQualityScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new AnalysisProperties();
        calculator = new AudioQualityScoreCalculator(properties,
                new LoudnessEvaluator(properties));
    }

    @Test
    void noProblemsScoresOneHundred() {
        assertEquals(100, calculator.calculate(null, List.of()).score());
    }

    @Test
    void configuredIssuePenaltiesAreApplied() {
        assertEquals(98, score(issue("SILENCE", "LOW", 0, 1000)));
        assertEquals(94, score(issue("VOLUME_DROP", "MEDIUM", 0, 1000)));
        assertEquals(88, score(issue("VOLUME_SPIKE", "HIGH", 0, 1000)));
        assertEquals(92, score(issue("NOISE_RISK", "MEDIUM", 0, 1000)));
    }

    @Test
    void loudnessPeakAndDynamicRangePenaltiesAreApplied() {
        AudioAnalysisResult result = loudnessResult("-30", "1", "0");
        assertEquals(83, calculator.calculate(result, List.of()).score());
    }

    @Test
    void scoreIsClampedToZeroAndOneHundred() {
        List<AudioIssueSegment> issues = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            issues.add(issue("NOISE_RISK", "HIGH",
                    index * 2000L, index * 2000L + 1000));
        }
        assertEquals(0, calculator.calculate(null, issues).score());
        properties.getReport().getScore().setSilenceLow(-10);
        assertEquals(100, score(issue("SILENCE", "LOW", 0, 1000)));
    }

    @Test
    void highlyOverlappingIssuesReceiveConfiguredDiscount() {
        List<AudioIssueSegment> issues = List.of(
                issue("NOISE_RISK", "HIGH", 0, 10000),
                issue("VOLUME_SPIKE", "HIGH", 1000, 9000));
        int score = calculator.calculate(null, issues).score();
        assertEquals(79, score);
        assertTrue(score > 73, "overlap must reduce repeated deduction");
    }

    @Test
    void repeatedSameTypeOverlapDoesNotDeductWithoutLimit() {
        List<AudioIssueSegment> issues = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            issues.add(issue("NOISE_RISK", "HIGH", 0, 10000));
        }
        assertEquals(85, calculator.calculate(null, issues).score());
    }

    @Test
    void excessiveSilenceRatioAddsConfiguredPenalty() {
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setSilenceRatio(new BigDecimal("0.50"));
        assertEquals(93, calculator.calculate(result,
                List.of(issue("SILENCE", "LOW", 0, 1000))).score());
    }

    @Test
    void gradeBoundariesAreCentralizedAndConfigurable() {
        AnalysisProperties.Grade grade = properties.getReport().getGrade();
        assertEquals(QualityGrade.EXCELLENT,
                QualityGrade.fromScore(90, grade));
        assertEquals(QualityGrade.GOOD,
                QualityGrade.fromScore(75, grade));
        assertEquals(QualityGrade.FAIR,
                QualityGrade.fromScore(60, grade));
        assertEquals(QualityGrade.POOR,
                QualityGrade.fromScore(59, grade));
    }

    private int score(AudioIssueSegment issue) {
        return calculator.calculate(null, List.of(issue)).score();
    }

    private AudioIssueSegment issue(String type, String severity,
                                    long start, long end) {
        AudioIssueSegment issue = new AudioIssueSegment();
        issue.setIssueType(type);
        issue.setSeverity(severity);
        issue.setStartMs(start);
        issue.setEndMs(end);
        issue.setDurationMs(end - start);
        return issue;
    }

    private AudioAnalysisResult loudnessResult(String integrated,
                                               String range,
                                               String truePeak) {
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setIntegratedLoudnessLufs(new BigDecimal(integrated));
        result.setLoudnessRangeLu(new BigDecimal(range));
        result.setTruePeakDbfs(new BigDecimal(truePeak));
        return result;
    }
}
