package com.audioagent.analysis.loudness;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the final ebur128 Summary block. */
@Slf4j
@Component
public class Ebur128OutputParser {

    private static final Pattern INTEGRATED = Pattern.compile(
            "^\\s*I:\\s*(\\S+)\\s+LUFS\\s*$");
    private static final Pattern LRA = Pattern.compile(
            "^\\s*LRA:\\s*(\\S+)\\s+LU\\s*$");
    private static final Pattern PEAK = Pattern.compile(
            "^\\s*Peak:\\s*(\\S+)\\s+dBFS\\s*$");
    private static final Pattern FRAME_TIMESTAMP = Pattern.compile(
            "\\bpts_time:([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern FRAME_METRIC = Pattern.compile(
            "lavfi\\.r128\\.(M|S)=(\\S+)");
    private static final int SUMMARY_LINE_LIMIT = 64;

    private static final BigDecimal MIN_DB_VALUE =
            BigDecimal.valueOf(-200);
    private static final BigDecimal MAX_DB_VALUE =
            BigDecimal.valueOf(100);
    private static final BigDecimal MIN_LRA = BigDecimal.ZERO;
    private static final BigDecimal MAX_LRA =
            BigDecimal.valueOf(200);

    public LoudnessMetrics parse(List<String> lines) {
        int summaryIndex = findFinalSummary(lines);
        if (summaryIndex < 0) {
            throw new IllegalArgumentException(
                    "Final ebur128 Summary was not found");
        }

        Section section = Section.NONE;
        BigDecimal integrated = null;
        BigDecimal lra = null;
        BigDecimal samplePeak = null;
        BigDecimal truePeak = null;

        for (int i = summaryIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String normalized = line.trim();
            section = switch (normalized) {
                case "Integrated loudness:" -> Section.INTEGRATED;
                case "Loudness range:" -> Section.LRA;
                case "Sample peak:" -> Section.SAMPLE_PEAK;
                case "True peak:" -> Section.TRUE_PEAK;
                default -> section;
            };

            Matcher matcher;
            switch (section) {
                case INTEGRATED -> {
                    matcher = INTEGRATED.matcher(line);
                    if (matcher.matches()) {
                        integrated = parseNumber(matcher.group(1));
                    }
                }
                case LRA -> {
                    matcher = LRA.matcher(line);
                    if (matcher.matches()) {
                        lra = parseNumber(matcher.group(1));
                    }
                }
                case SAMPLE_PEAK -> {
                    matcher = PEAK.matcher(line);
                    if (matcher.matches()) {
                        samplePeak = parseNumber(matcher.group(1));
                    }
                }
                case TRUE_PEAK -> {
                    matcher = PEAK.matcher(line);
                    if (matcher.matches()) {
                        truePeak = parseNumber(matcher.group(1));
                    }
                }
                default -> {
                }
            }
        }

        if (integrated == null) {
            throw new IllegalArgumentException(
                    "Integrated loudness is missing or non-finite");
        }
        validateRange("integrated loudness", integrated,
                MIN_DB_VALUE, MAX_DB_VALUE);
        validateOptionalRange("loudness range", lra,
                MIN_LRA, MAX_LRA);
        validateOptionalRange("sample peak", samplePeak,
                MIN_DB_VALUE, MAX_DB_VALUE);
        validateOptionalRange("true peak", truePeak,
                MIN_DB_VALUE, MAX_DB_VALUE);

        return new LoudnessMetrics(integrated, lra, samplePeak, truePeak);
    }

    public Accumulator newAccumulator() {
        return new Accumulator();
    }

    private int findFinalSummary(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains("Summary:")) {
                return i;
            }
        }
        return -1;
    }

    private BigDecimal parseNumber(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("inf") || normalized.contains("nan")) {
            return null;
        }
        try {
            return new BigDecimal(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validateOptionalRange(String name, BigDecimal value,
                                       BigDecimal min, BigDecimal max) {
        if (value != null) {
            validateRange(name, value, min, max);
        }
    }

    private void validateRange(String name, BigDecimal value,
                               BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            log.warn("Parsed ebur128 metric is outside the supported "
                    + "storage range, metric={}, value={}", name, value);
            throw new IllegalArgumentException(
                    "Invalid ebur128 " + name + " value");
        }
    }

    private enum Section {
        NONE,
        INTEGRATED,
        LRA,
        SAMPLE_PEAK,
        TRUE_PEAK
    }

    /**
     * Incrementally consumes FFmpeg stderr and retains only parsed frames
     * plus the bounded final Summary block.
     */
    public final class Accumulator {

        private final List<LoudnessFrame> frames = new ArrayList<>();
        private final List<String> summaryLines = new ArrayList<>();

        private Long timestampMs;
        private BigDecimal momentaryLufs;
        private BigDecimal shortTermLufs;
        private boolean momentarySeen;
        private boolean shortTermSeen;
        private boolean summaryStarted;

        public void accept(String line) {
            if (line.contains("Summary:")) {
                flushFrame();
                summaryStarted = true;
                summaryLines.clear();
            }
            if (summaryStarted) {
                if (summaryLines.size() < SUMMARY_LINE_LIMIT) {
                    summaryLines.add(line);
                }
                return;
            }

            Matcher timestampMatcher = FRAME_TIMESTAMP.matcher(line);
            if (timestampMatcher.find()) {
                flushFrame();
                timestampMs = parseTimestampMs(timestampMatcher.group(1));
                return;
            }

            Matcher metricMatcher = FRAME_METRIC.matcher(line);
            if (!metricMatcher.find() || timestampMs == null) {
                return;
            }
            BigDecimal value = parseNumber(metricMatcher.group(2));
            if ("M".equals(metricMatcher.group(1))) {
                momentarySeen = true;
                momentaryLufs = value;
            } else {
                shortTermSeen = true;
                shortTermLufs = value;
            }
        }

        public LoudnessAnalysis finish() {
            flushFrame();
            return new LoudnessAnalysis(
                    Ebur128OutputParser.this.parse(summaryLines),
                    List.copyOf(frames));
        }

        private Long parseTimestampMs(String seconds) {
            try {
                long value = new BigDecimal(seconds)
                        .movePointRight(3)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                return value < 0 ? null : value;
            } catch (NumberFormatException | ArithmeticException e) {
                log.debug("Ignoring invalid ebur128 frame timestamp");
                return null;
            }
        }

        private void flushFrame() {
            if (timestampMs != null && (momentarySeen || shortTermSeen)) {
                frames.add(new LoudnessFrame(timestampMs,
                        momentaryLufs, shortTermLufs));
            }
            timestampMs = null;
            momentaryLufs = null;
            shortTermLufs = null;
            momentarySeen = false;
            shortTermSeen = false;
        }
    }
}
