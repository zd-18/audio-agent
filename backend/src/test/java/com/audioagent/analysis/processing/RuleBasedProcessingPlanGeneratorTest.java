package com.audioagent.analysis.processing;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;
import com.audioagent.setting.model.UserProcessingPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedProcessingPlanGeneratorTest {

    private RuleBasedProcessingPlanGenerator generator;

    @BeforeEach
    void setUp() {
        AnalysisProperties properties = new AnalysisProperties();
        generator = new RuleBasedProcessingPlanGenerator(properties,
                new ProcessingOperationCatalog(),
                new ProcessingPlanSummaryBuilder(properties));
    }

    @Test
    void generatesOnlyExecutableNormalizeTrimAndDenoise() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "SILENCE", "HIGH", 1_000, 3_000),
                        issue(2, "VOLUME_DROP", "HIGH", 3_000, 4_000),
                        issue(3, "NOISE_RISK", "HIGH", 4_000, 5_000)),
                loudnessReport("LOW"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertEquals(List.of(ProcessingOperationType.TRIM_SEGMENT,
                        ProcessingOperationType.DENOISE,
                        ProcessingOperationType.NORMALIZE_VOLUME),
                plan.steps().stream().map(
                        ProcessingStepDraft::operationType).toList());
        assertTrue(plan.steps().stream().allMatch(step ->
                step.operationType().isExecutable()));
    }

    @Test
    void highNoiseRiskPlansStrongWholeAudioDenoise() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "NOISE_RISK", "HIGH", 0, 8_000)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.CONSERVATIVE));

        assertEquals(List.of(ProcessingOperationType.DENOISE),
                plan.steps().stream().map(
                        ProcessingStepDraft::operationType).toList());
        ProcessingStepDraft denoise = plan.steps().getFirst();
        assertEquals("STRONG", denoise.parameters().get("strength"));
        assertEquals(ProcessingPriority.HIGH, denoise.priority());
        assertEquals(null, denoise.startMs());
        assertEquals(null, denoise.endMs());
        assertEquals(8_000L, plan.estimatedOutputDurationMs());
    }

    @Test
    void mediumNoiseRiskPlansMediumDenoise() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "NOISE_RISK", "MEDIUM", 1_000, 4_000)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        ProcessingStepDraft denoise = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.DENOISE,
                denoise.operationType());
        assertEquals("MEDIUM", denoise.parameters().get("strength"));
        assertEquals(ProcessingPriority.MEDIUM, denoise.priority());
    }

    @Test
    void lowNoiseRiskDoesNotPlanDenoise() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "NOISE_RISK", "LOW", 1_000, 4_000)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void multipleNoiseRisksAggregateIntoSingleStrongestDenoise() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "NOISE_RISK", "MEDIUM", 0, 2_000),
                        issue(2, "NOISE_RISK", "HIGH", 3_000, 6_000),
                        issue(3, "NOISE_RISK", "LOW", 6_000, 7_000)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertEquals(1, plan.steps().size());
        ProcessingStepDraft denoise = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.DENOISE,
                denoise.operationType());
        assertEquals("STRONG", denoise.parameters().get("strength"));
        assertEquals(2L, denoise.sourceIssueId());
    }

    @Test
    void trimSegmentUsesExactIssueRangeWithoutKeepParameters() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "SILENCE", "HIGH", 1_250, 3_750)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        ProcessingStepDraft trim = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.TRIM_SEGMENT,
                trim.operationType());
        assertEquals(1_250L, trim.startMs());
        assertEquals(3_750L, trim.endMs());
        assertTrue(!trim.parameters().containsKey("suggestedKeepHeadMs"));
        assertTrue(!trim.parameters().containsKey("suggestedKeepTailMs"));
    }

    @Test
    void normalizeVolumeUsesConfiguredSafeParameters() {
        ProcessingPlanDraft plan = generate(List.of(),
                loudnessReport("HIGH"), 8_000,
                preferences(ProcessingStrategy.CONSERVATIVE));

        ProcessingStepDraft normalize = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.NORMALIZE_VOLUME,
                normalize.operationType());
        assertEquals(new BigDecimal("-16"),
                normalize.parameters().get("targetLufs"));
        assertEquals(new BigDecimal("-1"),
                normalize.parameters().get("truePeakLimitDbfs"));
    }

    @Test
    void peakRiskUsesNormalizeVolumeInsteadOfLegacyLimitPeak() {
        ProcessingPlanDraft plan = generate(List.of(),
                loudnessReport("NORMAL", "RISK"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertEquals(1, plan.steps().size());
        ProcessingStepDraft normalize = plan.steps().getFirst();
        assertEquals(ProcessingOperationType.NORMALIZE_VOLUME,
                normalize.operationType());
        assertEquals(new BigDecimal("-1"),
                normalize.parameters().get("truePeakLimitDbfs"));
    }

    @Test
    void everyGeneratedStepIsExecutableInCurrentStage() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "SILENCE", "HIGH", 1_000, 3_000),
                        issue(2, "VOLUME_SPIKE", "HIGH", 3_000, 4_000)),
                loudnessReport("NORMAL", "RISK"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertTrue(plan.steps().stream().allMatch(step ->
                step.operationType().isExecutable()));
    }

    @Test
    void estimatedDurationMergesOverlappingTrimRanges() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "SILENCE", "HIGH", 1_000, 5_000),
                        issue(2, "SILENCE", "HIGH", 4_000, 7_000)),
                loudnessReport("NORMAL"), 10_000,
                preferences(ProcessingStrategy.BALANCED));

        assertEquals(4_000L, plan.estimatedOutputDurationMs());
    }

    @Test
    void conservativeStrategyDoesNotAutoProposeMediumTrim() {
        ProcessingPlanDraft plan = generate(List.of(
                        issue(1, "SILENCE", "MEDIUM", 1_000, 3_000)),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.CONSERVATIVE));

        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void duplicateIssueDoesNotCreateDuplicateTrim() {
        AudioIssueSegment same = issue(1, "SILENCE", "HIGH",
                1_000, 3_000);
        ProcessingPlanDraft plan = generate(List.of(same, same),
                loudnessReport("NORMAL"), 8_000,
                preferences(ProcessingStrategy.BALANCED));

        assertEquals(1, plan.steps().size());
    }

    private ProcessingPlanDraft generate(List<AudioIssueSegment> issues,
                                         AudioAnalysisReportVO report,
                                         long durationMs,
                                         UserProcessingPreferences preferences) {
        AudioAnalysisTask task = new AudioAnalysisTask();
        task.setId(100L);
        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setDurationMs(durationMs);
        return generator.generate(new ProcessingPlanContext(task, result,
                report, issues, preferences));
    }

    private UserProcessingPreferences preferences(
            ProcessingStrategy strategy) {
        return new UserProcessingPreferences(DenoiseStrength.LIGHT,
                strategy, true, true);
    }

    private AudioAnalysisReportVO loudnessReport(String level) {
        return loudnessReport(level, "SAFE");
    }

    private AudioAnalysisReportVO loudnessReport(String level,
                                                  String peakRisk) {
        return AudioAnalysisReportVO.builder()
                .loudnessOverview(AudioAnalysisReportVO.LoudnessOverview
                        .builder().loudnessLevel(level)
                        .peakRisk(peakRisk).build())
                .build();
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
