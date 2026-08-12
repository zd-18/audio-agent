package com.audioagent.processing.pipeline;

import com.audioagent.analysis.processing.ProcessingOperationType;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SilenceTrimPlanner {

    public Plan plan(long durationMs,
                     List<ExecutableProcessingStep> steps) {
        if (durationMs <= 0) {
            throw invalid("Source duration is invalid");
        }
        List<Range> removals = new ArrayList<>();
        for (ExecutableProcessingStep step : steps) {
            long start = requiredTime(step.startMs(), "startMs");
            long end = requiredTime(step.endMs(), "endMs");
            long head = step.operationType()
                    == ProcessingOperationType.TRIM_SEGMENT ? 0
                    : wholeMilliseconds(step.parameters(),
                    "suggestedKeepHeadMs");
            long tail = step.operationType()
                    == ProcessingOperationType.TRIM_SEGMENT ? 0
                    : wholeMilliseconds(step.parameters(),
                    "suggestedKeepTailMs");
            long removeStart = Math.max(0, Math.min(durationMs,
                    safeAdd(start, head)));
            long removeEnd = Math.max(0, Math.min(durationMs,
                    safeSubtract(end, tail)));
            if (removeEnd > removeStart) {
                removals.add(new Range(removeStart, removeEnd));
            }
        }
        List<Range> merged = merge(removals);
        List<Range> retained = new ArrayList<>();
        long cursor = 0;
        for (Range removal : merged) {
            if (removal.startMs() > cursor) {
                retained.add(new Range(cursor, removal.startMs()));
            }
            cursor = Math.max(cursor, removal.endMs());
        }
        if (cursor < durationMs) {
            retained.add(new Range(cursor, durationMs));
        }
        long retainedDuration = retained.stream()
                .mapToLong(Range::durationMs).sum();
        if (retainedDuration <= 0 || retained.isEmpty()) {
            throw invalid("Silence trimming would produce empty audio");
        }
        return new Plan(List.copyOf(merged), List.copyOf(retained),
                retainedDuration);
    }

    public List<Range> merge(List<Range> source) {
        List<Range> sorted = source.stream()
                .filter(range -> range != null
                        && range.startMs() >= 0
                        && range.endMs() > range.startMs())
                .sorted(Comparator.comparingLong(Range::startMs)
                        .thenComparingLong(Range::endMs))
                .toList();
        List<Range> merged = new ArrayList<>();
        for (Range range : sorted) {
            if (merged.isEmpty()) {
                merged.add(range);
                continue;
            }
            Range previous = merged.getLast();
            if (range.startMs() <= previous.endMs()) {
                merged.set(merged.size() - 1,
                        new Range(previous.startMs(),
                                Math.max(previous.endMs(), range.endMs())));
            } else {
                merged.add(range);
            }
        }
        return List.copyOf(merged);
    }

    private long wholeMilliseconds(java.util.Map<String, Object> parameters,
                                   String name) {
        Object value = parameters.get(name);
        if (!(value instanceof Number number)) {
            throw invalid(name + " must be a whole number of milliseconds");
        }
        try {
            long result = new BigDecimal(number.toString()).longValueExact();
            if (result < 0) {
                throw invalid(name + " cannot be negative");
            }
            return result;
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid(name + " must be a whole number of milliseconds");
        }
    }

    private long requiredTime(Long value, String name) {
        if (value == null || value < 0) {
            throw invalid(name + " is invalid");
        }
        return value;
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw invalid("Trim range overflow");
        }
    }

    private long safeSubtract(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException e) {
            throw invalid("Trim range overflow");
        }
    }

    private ProcessingExecutionException invalid(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                false, message);
    }

    public record Range(long startMs, long endMs) {
        public long durationMs() {
            return endMs - startMs;
        }
    }

    public record Plan(List<Range> removed, List<Range> retained,
                       long retainedDurationMs) {
    }
}
