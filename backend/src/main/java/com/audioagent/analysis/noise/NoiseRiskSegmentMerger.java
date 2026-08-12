package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NoiseRiskSegmentMerger {

    private final AnalysisProperties properties;
    private final NoiseRiskEvaluator evaluator;

    public List<NoiseRiskSegment> merge(List<NoiseRiskSampleWindow> windows,
                                        long audioDurationMs) {
        if (windows.isEmpty() || audioDurationMs <= 0) {
            return List.of();
        }
        AnalysisProperties.NoiseRisk config = properties.getNoiseRisk();
        List<NoiseRiskSegment> segments = new ArrayList<>();
        List<NoiseRiskSampleWindow> current = new ArrayList<>();

        for (NoiseRiskSampleWindow window : windows) {
            if (!current.isEmpty()) {
                long gap = window.startMs()
                        - current.get(current.size() - 1).endMs();
                if (gap > config.getMergeGapMs()) {
                    addIfLongEnough(segments, current, audioDurationMs,
                            config);
                    current.clear();
                }
            }
            current.add(window);
        }
        addIfLongEnough(segments, current, audioDurationMs, config);
        return List.copyOf(segments);
    }

    private void addIfLongEnough(List<NoiseRiskSegment> target,
                                 List<NoiseRiskSampleWindow> windows,
                                 long audioDurationMs,
                                 AnalysisProperties.NoiseRisk config) {
        if (windows.isEmpty()) {
            return;
        }
        long startMs = Math.max(0, windows.get(0).startMs());
        long endMs = Math.min(audioDurationMs,
                windows.get(windows.size() - 1).endMs());
        long durationMs = Math.max(0, endMs - startMs);
        if (durationMs < config.getMinDurationMs()) {
            return;
        }
        BigDecimal averageRms = average(windows.stream()
                .map(window -> window.frame().rmsDbfs()).toList());
        BigDecimal averageFlatness = average(windows.stream()
                .map(window -> window.frame().spectralFlatness()).toList());
        BigDecimal averageEntropy = average(windows.stream()
                .map(window -> window.frame().spectralEntropy()).toList());
        BigDecimal averageScore = average(windows.stream()
                .map(NoiseRiskSampleWindow::score).toList());
        BigDecimal maximumScore = windows.stream()
                .map(NoiseRiskSampleWindow::score)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal minimumRms = windows.stream()
                .map(window -> window.frame().rmsDbfs())
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maximumRms = windows.stream()
                .map(window -> window.frame().rmsDbfs())
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        long spectralCount = windows.stream()
                .filter(NoiseRiskSampleWindow::spectralMetricsAvailable)
                .count();
        String confidence = spectralCount == 0 ? "LOW"
                : spectralCount == windows.size() ? "HIGH" : "MEDIUM";

        target.add(new NoiseRiskSegment(startMs, endMs, durationMs,
                scale(averageRms), scale(averageFlatness),
                scale(averageEntropy),
                scale(maximumRms.subtract(minimumRms)),
                scaleScore(averageScore), scaleScore(maximumScore),
                confidence, windows.size(),
                evaluator.severity(durationMs, averageScore)));
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> valid = values.stream().filter(value -> value != null)
                .toList();
        if (valid.isEmpty()) {
            return null;
        }
        BigDecimal sum = valid.stream().reduce(BigDecimal.ZERO,
                BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(valid.size()), 8,
                RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleScore(BigDecimal value) {
        return value == null ? null
                : value.setScale(4, RoundingMode.HALF_UP);
    }
}
