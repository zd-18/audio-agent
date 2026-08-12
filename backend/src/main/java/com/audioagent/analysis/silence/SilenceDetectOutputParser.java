package com.audioagent.analysis.silence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SilenceDetectOutputParser {

    private static final Pattern START_PATTERN = Pattern.compile(
            "silence_start:\\s*([-+]?\\d+(?:\\.\\d+)?)");
    private static final Pattern END_PATTERN = Pattern.compile(
            "silence_end:\\s*([-+]?\\d+(?:\\.\\d+)?)");
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "silence_duration:\\s*([-+]?\\d+(?:\\.\\d+)?)");
    private static final BigDecimal MILLIS = BigDecimal.valueOf(1000);

    public List<SilenceSegment> parse(List<String> lines,
                                      long audioDurationMs,
                                      long minDurationMs) {
        List<SilenceSegment> segments = new ArrayList<>();
        BigDecimal pendingStart = null;

        for (String line : lines) {
            try {
                Matcher startMatcher = START_PATTERN.matcher(line);
                if (startMatcher.find()) {
                    pendingStart = new BigDecimal(startMatcher.group(1));
                    continue;
                }

                Matcher endMatcher = END_PATTERN.matcher(line);
                if (!endMatcher.find()) {
                    continue;
                }

                BigDecimal end = new BigDecimal(endMatcher.group(1));
                BigDecimal start = pendingStart;
                if (start == null) {
                    Matcher durationMatcher = DURATION_PATTERN.matcher(line);
                    if (durationMatcher.find()) {
                        start = end.subtract(
                                new BigDecimal(durationMatcher.group(1)));
                    }
                }

                if (start != null) {
                    addValidated(segments, start, end, audioDurationMs,
                            minDurationMs);
                } else {
                    log.debug("Ignoring silence_end without a usable start");
                }
                pendingStart = null;
            } catch (RuntimeException e) {
                log.debug("Ignoring malformed silencedetect log line: {}",
                        abbreviate(line, 300));
            }
        }

        if (pendingStart != null) {
            addValidated(segments, pendingStart,
                    millisToSeconds(audioDurationMs), audioDurationMs,
                    minDurationMs);
        }
        return segments;
    }

    private void addValidated(List<SilenceSegment> segments,
                              BigDecimal rawStart,
                              BigDecimal rawEnd,
                              long audioDurationMs,
                              long minDurationMs) {
        long startMs = Math.max(0, secondsToMillis(rawStart));
        long endMs = Math.max(0, secondsToMillis(rawEnd));

        if (audioDurationMs >= 0) {
            startMs = Math.min(startMs, audioDurationMs);
            endMs = Math.min(endMs, audioDurationMs);
        }
        if (endMs < startMs) {
            log.debug("Ignoring silence segment whose end precedes its start");
            return;
        }

        long durationMs = endMs - startMs;
        if (durationMs < minDurationMs) {
            return;
        }
        segments.add(new SilenceSegment(startMs, endMs, durationMs));
    }

    private long secondsToMillis(BigDecimal seconds) {
        return seconds.multiply(MILLIS)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal millisToSeconds(long millis) {
        return BigDecimal.valueOf(millis).divide(MILLIS);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
