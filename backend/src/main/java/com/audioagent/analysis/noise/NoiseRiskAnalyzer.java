package com.audioagent.analysis.noise;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoiseRiskAnalyzer {

    private final AnalysisProperties properties;
    private final NoiseRiskEvaluator evaluator;
    private final NoiseRiskSegmentMerger segmentMerger;

    public List<NoiseRiskSegment> analyze(List<NoiseAnalysisFrame> inputFrames,
                                          long audioDurationMs) {
        AnalysisProperties.NoiseRisk config = properties.getNoiseRisk();
        if (!config.isEnabled() || inputFrames == null
                || inputFrames.isEmpty() || audioDurationMs <= 0) {
            if (config.isEnabled() && (inputFrames == null
                    || inputFrames.isEmpty())) {
                log.warn("Noise risk analysis skipped because no valid frames were parsed");
            }
            return List.of();
        }

        List<NoiseAnalysisFrame> frames = normalize(inputFrames);
        Deque<BigDecimal> rmsWindow = new ArrayDeque<>();
        List<NoiseRiskSampleWindow> candidates = new ArrayList<>();
        for (int index = 0; index < frames.size(); index++) {
            NoiseAnalysisFrame frame = frames.get(index);
            if (frame.rmsDbfs() == null) {
                continue;
            }
            rmsWindow.addLast(frame.rmsDbfs());
            while (rmsWindow.size() > config.getStabilityWindowSize()) {
                rmsWindow.removeFirst();
            }
            if (rmsWindow.size() < 2) {
                continue;
            }
            BigDecimal variation = variation(rmsWindow);
            NoiseFrameEvaluation evaluation = evaluator.evaluate(frame,
                    variation);
            if (!evaluation.candidate()) {
                continue;
            }
            long startMs = Math.min(audioDurationMs, frame.timestampMs());
            long endMs = resolveEnd(frames, index, audioDurationMs,
                    config.getFrameDurationMs());
            if (endMs > startMs) {
                candidates.add(new NoiseRiskSampleWindow(startMs, endMs,
                        frame, evaluation.score(),
                        evaluation.spectralMetricsAvailable()));
            }
        }
        return segmentMerger.merge(candidates, audioDurationMs);
    }

    private List<NoiseAnalysisFrame> normalize(
            List<NoiseAnalysisFrame> frames) {
        TreeMap<Long, NoiseAnalysisFrame> byTimestamp = new TreeMap<>();
        frames.stream().filter(frame -> frame.timestampMs() >= 0)
                .sorted(Comparator.comparingLong(
                        NoiseAnalysisFrame::timestampMs))
                .forEach(frame -> byTimestamp.put(frame.timestampMs(), frame));
        return List.copyOf(byTimestamp.values());
    }

    private BigDecimal variation(Deque<BigDecimal> values) {
        BigDecimal min = values.stream().min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return max.subtract(min);
    }

    private long resolveEnd(List<NoiseAnalysisFrame> frames, int index,
                            long durationMs, long frameDurationMs) {
        long start = frames.get(index).timestampMs();
        if (index + 1 < frames.size()) {
            long next = frames.get(index + 1).timestampMs();
            if (next > start) {
                return Math.min(durationMs,
                        Math.min(next, start + frameDurationMs));
            }
        }
        return Math.min(durationMs, start + frameDurationMs);
    }
}
