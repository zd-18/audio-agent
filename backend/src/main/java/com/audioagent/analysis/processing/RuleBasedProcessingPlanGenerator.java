package com.audioagent.analysis.processing;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.setting.config.UserSettingDefaults;
import com.audioagent.setting.enums.ProcessingStrategy;
import com.audioagent.setting.model.UserProcessingPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedProcessingPlanGenerator
        implements ProcessingPlanGenerator {

    private static final String REVIEW_BEFORE_APPLY = "REVIEW_BEFORE_APPLY";
    private static final String SUGGESTION_ONLY = "SUGGESTION_ONLY";

    private final AnalysisProperties properties;
    private final ProcessingOperationCatalog catalog;
    private final ProcessingPlanSummaryBuilder summaryBuilder;

    @Override
    public ProcessingPlanDraft generate(ProcessingPlanContext context) {
        UserProcessingPreferences preferences = context.preferences() == null
                ? UserSettingDefaults.processingPreferences()
                : context.preferences();
        List<ProcessingStepDraft> candidates = new ArrayList<>();
        List<AudioIssueSegment> issues = context.issues() == null
                ? List.of() : context.issues();
        for (AudioIssueSegment issue : issues) {
            ProcessingStepDraft trim = trimFromIssue(issue, preferences);
            if (trim != null) {
                candidates.add(trim);
            }
        }
        addNormalizeStep(context.report(), candidates, preferences);

        List<ProcessingStepDraft> deduplicated = deduplicate(candidates);
        deduplicated.sort(stepComparator());
        int maxSteps = properties.getProcessingPlan().getMaxSteps();
        int trimmedCount = Math.max(0, deduplicated.size() - maxSteps);
        List<ProcessingStepDraft> finalSteps = deduplicated.size() > maxSteps
                ? new ArrayList<>(deduplicated.subList(0, maxSteps))
                : deduplicated;
        Long estimatedDuration = estimateDuration(context.result(),
                finalSteps);
        log.info("Processing plan limited to executable operations, taskId={}, "
                        + "trimCandidates={}, finalSteps={}",
                context.task() == null ? null : context.task().getId(),
                candidates.stream().filter(step -> step.operationType()
                        == ProcessingOperationType.TRIM_SEGMENT).count(),
                finalSteps.size());
        return new ProcessingPlanDraft(ProcessingPlanStatus.READY,
                summaryBuilder.build(finalSteps), estimatedDuration,
                List.copyOf(finalSteps), 0, trimmedCount);
    }

    private ProcessingStepDraft trimFromIssue(
            AudioIssueSegment issue,
            UserProcessingPreferences preferences) {
        if (issue == null || issue.getIssueType() == null
                || !"SILENCE".equalsIgnoreCase(issue.getIssueType())
                || issue.getStartMs() == null || issue.getEndMs() == null
                || issue.getStartMs() < 0
                || issue.getEndMs() <= issue.getStartMs()) {
            return null;
        }
        ProcessingPriority priority = ProcessingPriority.fromSeverity(
                issue.getSeverity());
        boolean executable = priority == ProcessingPriority.HIGH
                ? properties.getProcessingPlan().getSilence()
                .isHighTrimEnabled()
                : priority == ProcessingPriority.MEDIUM
                && preferences.processingStrategy()
                == ProcessingStrategy.BALANCED
                && properties.getProcessingPlan().getSilence()
                .isMediumTrimEnabled();
        if (!executable) {
            return null;
        }
        return build(ProcessingOperationType.TRIM_SEGMENT, issue.getId(),
                issue.getStartMs(), issue.getEndMs(), priority,
                Map.of("mode", REVIEW_BEFORE_APPLY));
    }

    private void addNormalizeStep(
            AudioAnalysisReportVO report,
            List<ProcessingStepDraft> candidates,
            UserProcessingPreferences preferences) {
        if (report == null || report.getLoudnessOverview() == null) {
            return;
        }
        String loudnessLevel = report.getLoudnessOverview()
                .getLoudnessLevel();
        boolean loudnessNeedsNormalization =
                "LOW".equalsIgnoreCase(loudnessLevel)
                || "HIGH".equalsIgnoreCase(loudnessLevel);
        boolean peakNeedsNormalization = preferences.autoLimitPeak()
                && "RISK".equalsIgnoreCase(report.getLoudnessOverview()
                .getPeakRisk());
        if (!loudnessNeedsNormalization && !peakNeedsNormalization) {
            return;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("targetLufs", properties.getLoudness()
                .getTargetLufs());
        parameters.put("truePeakLimitDbfs", properties.getLoudness()
                .getTruePeakLimitDbfs());
        parameters.put("mode", SUGGESTION_ONLY);
        candidates.add(build(ProcessingOperationType.NORMALIZE_VOLUME,
                null, null, null, ProcessingPriority.MEDIUM,
                Map.copyOf(parameters)));
    }

    private ProcessingStepDraft build(
            ProcessingOperationType operation, Long sourceIssueId,
            Long startMs, Long endMs, ProcessingPriority priority,
            Map<String, Object> parameters) {
        ProcessingRiskLevel risk = operation
                == ProcessingOperationType.TRIM_SEGMENT
                ? ProcessingRiskLevel.HIGH : ProcessingRiskLevel.MEDIUM;
        return new ProcessingStepDraft(operation, catalog.title(operation),
                catalog.description(operation, startMs, endMs),
                sourceIssueId, startMs, endMs, priority, risk, true,
                parameters, catalog.reason(operation));
    }

    private List<ProcessingStepDraft> deduplicate(
            List<ProcessingStepDraft> candidates) {
        Set<String> seen = new LinkedHashSet<>();
        List<ProcessingStepDraft> result = new ArrayList<>();
        for (ProcessingStepDraft candidate : candidates) {
            String key = candidate.operationType() + ":"
                    + candidate.sourceIssueId();
            if (seen.add(key)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private Comparator<ProcessingStepDraft> stepComparator() {
        return Comparator
                .comparingInt((ProcessingStepDraft step) ->
                        step.priority().rank())
                .thenComparingInt(step -> step.startMs() == null ? 1 : 0)
                .thenComparing(ProcessingStepDraft::startMs,
                        Comparator.nullsLast(Long::compareTo))
                .thenComparing(ProcessingStepDraft::endMs,
                        Comparator.nullsLast(Long::compareTo))
                .thenComparing(step -> step.operationType().ordinal());
    }

    private Long estimateDuration(AudioAnalysisResult result,
                                  List<ProcessingStepDraft> steps) {
        if (result == null || result.getDurationMs() == null
                || result.getDurationMs() < 0) {
            return null;
        }
        long duration = result.getDurationMs();
        List<Range> ranges = steps.stream()
                .filter(step -> step.operationType()
                        == ProcessingOperationType.TRIM_SEGMENT)
                .filter(step -> step.startMs() != null
                        && step.endMs() != null)
                .map(step -> new Range(
                        Math.min(duration, Math.max(0, step.startMs())),
                        Math.min(duration, Math.max(0, step.endMs()))))
                .filter(range -> range.end() > range.start())
                .sorted(Comparator.comparingLong(Range::start))
                .toList();
        long removed = 0;
        long currentStart = -1;
        long currentEnd = -1;
        for (Range range : ranges) {
            if (currentStart < 0) {
                currentStart = range.start();
                currentEnd = range.end();
            } else if (range.start() <= currentEnd) {
                currentEnd = Math.max(currentEnd, range.end());
            } else {
                removed += currentEnd - currentStart;
                currentStart = range.start();
                currentEnd = range.end();
            }
        }
        if (currentStart >= 0) {
            removed += currentEnd - currentStart;
        }
        return Math.max(0, duration - removed);
    }

    private record Range(long start, long end) {
    }
}
