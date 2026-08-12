package com.audioagent.analysis.noise;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NoiseFrameOutputParser {

    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\bpts_time:([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern METRIC = Pattern.compile(
            "(lavfi\\.(?:astats\\.Overall|aspectralstats\\.\\d+)\\.[A-Za-z_]+)=(\\S+)");

    public Accumulator newAccumulator(int spectralWindowSize) {
        return new Accumulator(spectralWindowSize);
    }

    public final class Accumulator {

        private final List<NoiseAnalysisFrame> frames = new ArrayList<>();
        private final double maximumSpectralEntropy;
        private Long timestampMs;
        private BigDecimal rmsDbfs;
        private BigDecimal peakDbfs;
        private BigDecimal noiseFloorDbfs;
        private BigDecimal flatnessSum = BigDecimal.ZERO;
        private int flatnessCount;
        private BigDecimal entropySum = BigDecimal.ZERO;
        private int entropyCount;
        private BigDecimal centroidSum = BigDecimal.ZERO;
        private int centroidCount;

        private Accumulator(int spectralWindowSize) {
            int bins = Math.max(2, spectralWindowSize / 2 + 1);
            this.maximumSpectralEntropy = Math.log(bins);
        }

        public void accept(String line) {
            Matcher timestampMatcher = TIMESTAMP.matcher(line);
            if (timestampMatcher.find()) {
                flushFrame();
                timestampMs = parseTimestampMs(timestampMatcher.group(1));
                return;
            }
            if (timestampMs == null) {
                return;
            }
            Matcher metricMatcher = METRIC.matcher(line);
            if (!metricMatcher.find()) {
                return;
            }
            BigDecimal value = parseFinite(metricMatcher.group(2));
            if (value == null) {
                return;
            }
            String key = metricMatcher.group(1);
            if (key.endsWith(".RMS_level")) {
                rmsDbfs = value;
            } else if (key.endsWith(".Peak_level")) {
                peakDbfs = value;
            } else if (key.endsWith(".Noise_floor")) {
                noiseFloorDbfs = value;
            } else if (key.endsWith(".flatness")) {
                flatnessSum = flatnessSum.add(value);
                flatnessCount++;
            } else if (key.endsWith(".entropy")) {
                entropySum = entropySum.add(value);
                entropyCount++;
            } else if (key.endsWith(".centroid")) {
                centroidSum = centroidSum.add(value);
                centroidCount++;
            }
        }

        public List<NoiseAnalysisFrame> finish() {
            flushFrame();
            return List.copyOf(frames);
        }

        private void flushFrame() {
            if (timestampMs != null && rmsDbfs != null) {
                frames.add(new NoiseAnalysisFrame(timestampMs, rmsDbfs,
                        peakDbfs, noiseFloorDbfs,
                        average(flatnessSum, flatnessCount),
                        normalizeEntropy(average(entropySum, entropyCount)),
                        average(centroidSum, centroidCount)));
            }
            timestampMs = null;
            rmsDbfs = null;
            peakDbfs = null;
            noiseFloorDbfs = null;
            flatnessSum = BigDecimal.ZERO;
            flatnessCount = 0;
            entropySum = BigDecimal.ZERO;
            entropyCount = 0;
            centroidSum = BigDecimal.ZERO;
            centroidCount = 0;
        }

        private BigDecimal normalizeEntropy(BigDecimal raw) {
            if (raw == null || maximumSpectralEntropy <= 0) {
                return null;
            }
            double normalized = raw.doubleValue() / maximumSpectralEntropy;
            if (!Double.isFinite(normalized)) {
                return null;
            }
            return BigDecimal.valueOf(Math.max(0, Math.min(1, normalized)))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        private BigDecimal average(BigDecimal sum, int count) {
            return count == 0 ? null : sum.divide(BigDecimal.valueOf(count),
                    8, RoundingMode.HALF_UP);
        }

        private Long parseTimestampMs(String seconds) {
            try {
                long value = new BigDecimal(seconds).movePointRight(3)
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                return value < 0 ? null : value;
            } catch (NumberFormatException | ArithmeticException e) {
                log.debug("Ignoring invalid noise frame timestamp");
                return null;
            }
        }

        private BigDecimal parseFinite(String token) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.contains("nan") || normalized.contains("inf")) {
                log.debug("Ignoring non-finite noise frame metric");
                return null;
            }
            try {
                return new BigDecimal(token);
            } catch (NumberFormatException e) {
                log.debug("Ignoring invalid noise frame metric");
                return null;
            }
        }
    }
}
