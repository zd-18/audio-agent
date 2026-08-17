package com.audioagent.processing.pipeline;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.exception.ProcessingExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compresses or removes long silent intervals detected in the audio.
 *
 * <p>COMPRESS shortens every silence longer than {@code minSilenceMs} to
 * {@code keepSilenceMs} so a short natural pause survives; REMOVE deletes
 * the whole interval. The trimmed audio is rebuilt with the same
 * atrim + concat technique used by {@link SilenceTrimProcessor}, so no
 * second execution framework is introduced. The source file is never
 * overwritten; the result is written to {@code output}.
 */
@Component
@RequiredArgsConstructor
public class SilenceCleanupProcessor {

    public static final String MODE_COMPRESS = "COMPRESS";
    public static final String MODE_REMOVE = "REMOVE";
    public static final long DEFAULT_MIN_SILENCE_MS = 3000;
    public static final long DEFAULT_KEEP_SILENCE_MS = 800;

    private final FfmpegCommandExecutor ffmpeg;
    private final LongSilenceDetector silenceDetector;
    private final SilenceTrimPlanner planner;

    /**
     * @param applied          whether any long silence was actually cleaned
     * @param expectedDurationMs resulting duration (input duration minus the
     *                          removed silence)
     * @param intervals        the detected long silence intervals
     */
    public record CleanupResult(boolean applied, long expectedDurationMs,
                                List<SilenceInterval> intervals) {
    }

    public CleanupResult process(Path input, Path output,
                                 long inputDurationMs,
                                 ExecutableProcessingStep step) {
        Parameters parameters = parseParameters(step.parameters());
        List<SilenceInterval> intervals = silenceDetector.detect(
                input, inputDurationMs, parameters.minSilenceMs());
        if (intervals.isEmpty()) {
            return new CleanupResult(false, inputDurationMs, List.of());
        }

        List<SilenceTrimPlanner.Range> removals = new ArrayList<>();
        for (SilenceInterval interval : intervals) {
            if (MODE_REMOVE.equals(parameters.mode())) {
                removals.add(new SilenceTrimPlanner.Range(
                        interval.startMs(), interval.endMs()));
                continue;
            }
            long keptUntil = interval.endMs() - parameters.keepSilenceMs();
            if (keptUntil > interval.startMs()) {
                removals.add(new SilenceTrimPlanner.Range(
                        interval.startMs(), keptUntil));
            }
        }
        List<SilenceTrimPlanner.Range> merged = planner.merge(removals);
        if (merged.isEmpty()) {
            // Every interval is already shorter than keepSilenceMs, so
            // nothing would actually change.
            return new CleanupResult(false, inputDurationMs,
                    List.copyOf(intervals));
        }

        List<SilenceTrimPlanner.Range> retained = new ArrayList<>();
        long cursor = 0;
        for (SilenceTrimPlanner.Range removal : merged) {
            if (removal.startMs() > cursor) {
                retained.add(new SilenceTrimPlanner.Range(
                        cursor, removal.startMs()));
            }
            cursor = Math.max(cursor, removal.endMs());
        }
        if (cursor < inputDurationMs) {
            retained.add(new SilenceTrimPlanner.Range(cursor, inputDurationMs));
        }
        long retainedDuration = retained.stream()
                .mapToLong(SilenceTrimPlanner.Range::durationMs).sum();
        if (retained.isEmpty() || retainedDuration <= 0) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false,
                    "Silence cleanup would leave no audible content");
        }

        render(input, output, retained);
        long removedMs = merged.stream()
                .mapToLong(range -> range.endMs() - range.startMs()).sum();
        return new CleanupResult(true,
                Math.max(0, inputDurationMs - removedMs),
                List.copyOf(intervals));
    }

    private void render(Path input, Path output,
                        List<SilenceTrimPlanner.Range> retained) {
        List<String> filters = new ArrayList<>();
        StringBuilder concatInputs = new StringBuilder();
        for (int index = 0; index < retained.size(); index++) {
            SilenceTrimPlanner.Range range = retained.get(index);
            String label = "keep" + index;
            filters.add("[0:a]atrim=start="
                    + FfmpegValueFormatter.seconds(range.startMs())
                    + ":end="
                    + FfmpegValueFormatter.seconds(range.endMs())
                    + ",asetpts=PTS-STARTPTS[" + label + "]");
            concatInputs.append('[').append(label).append(']');
        }
        filters.add(concatInputs + "concat=n=" + retained.size()
                + ":v=0:a=1[outa]");
        ffmpeg.transform(input, output, null,
                String.join(";", filters), "[outa]", "SILENCE_CLEANUP");
    }

    private Parameters parseParameters(Map<String, Object> parameters) {
        Map<String, Object> safe = parameters == null
                ? Map.of() : parameters;
        String mode = MODE_COMPRESS;
        Object rawMode = safe.get("mode");
        if (rawMode instanceof String modeValue) {
            if (!MODE_COMPRESS.equals(modeValue)
                    && !MODE_REMOVE.equals(modeValue)) {
                throw invalid("mode must be COMPRESS or REMOVE");
            }
            mode = modeValue;
        }
        long minSilenceMs = wholeMilliseconds(safe, "minSilenceMs",
                DEFAULT_MIN_SILENCE_MS);
        long keepSilenceMs = wholeMilliseconds(safe, "keepSilenceMs",
                DEFAULT_KEEP_SILENCE_MS);
        return new Parameters(mode, minSilenceMs, keepSilenceMs);
    }

    private long wholeMilliseconds(Map<String, Object> parameters,
                                   String name, long fallback) {
        Object value = parameters.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw invalid(name + " must be a whole number of milliseconds");
        }
        try {
            long result = new BigDecimal(number.toString()).longValueExact();
            if (result <= 0) {
                throw invalid(name + " must be positive");
            }
            return result;
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid(name + " must be a whole number of milliseconds");
        }
    }

    private ProcessingExecutionException invalid(String message) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                false, message);
    }

    private record Parameters(String mode, long minSilenceMs,
                              long keepSilenceMs) {
    }
}
