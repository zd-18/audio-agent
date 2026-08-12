package com.audioagent.analysis.volume;

import com.audioagent.analysis.loudness.LoudnessAnalysis;
import com.audioagent.analysis.loudness.LoudnessFrame;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolumeIssueAnalyzer {

    private final AnalysisProperties properties;
    private final VolumeIssueSegmentMerger segmentMerger;

    public List<VolumeIssueSegment> analyze(
            LoudnessAnalysis analysis,
            long audioDurationMs
    ) {
        AnalysisProperties.VolumeSegment config =
                properties.getVolumeSegment();
        if (!config.isEnabled() || analysis == null
                || analysis.metrics() == null
                || analysis.metrics().integratedLoudnessLufs() == null
                || audioDurationMs <= 0) {
            return List.of();
        }

        List<LoudnessFrame> frames = normalizeFrames(analysis.frames());
        if (frames.isEmpty()) {
            log.warn("Volume segment analysis skipped because no valid "
                    + "time-series frames were available");
            return List.of();
        }

        BigDecimal baseline =
                analysis.metrics().integratedLoudnessLufs();
        BigDecimal lowBoundary = baseline.subtract(
                config.getLowDeviationLu());
        BigDecimal highBoundary = baseline.add(
                config.getSpikeDeviationLu());
        List<VolumeSampleWindow> abnormalWindows = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {
            LoudnessFrame frame = frames.get(i);
            BigDecimal momentary = frame.momentaryLufs();
            if (momentary == null
                    || momentary.compareTo(
                    config.getMinimumValidLufs()) < 0) {
                continue;
            }
            VolumeIssueType issueType = null;
            if (momentary.compareTo(lowBoundary) < 0) {
                issueType = VolumeIssueType.VOLUME_DROP;
            } else if (momentary.compareTo(highBoundary) > 0) {
                issueType = VolumeIssueType.VOLUME_SPIKE;
            }
            if (issueType == null) {
                continue;
            }

            long startMs = Math.min(frame.timestampMs(), audioDurationMs);
            long endMs = resolveWindowEnd(frames, i, audioDurationMs);
            if (endMs > startMs) {
                abnormalWindows.add(new VolumeSampleWindow(issueType,
                        startMs, endMs, momentary));
            }
        }
        return segmentMerger.merge(abnormalWindows, audioDurationMs);
    }

    private List<LoudnessFrame> normalizeFrames(List<LoudnessFrame> frames) {
        TreeMap<Long, LoudnessFrame> byTimestamp = new TreeMap<>();
        frames.stream()
                .filter(frame -> frame.timestampMs() >= 0)
                .sorted(Comparator.comparingLong(
                        LoudnessFrame::timestampMs))
                .forEach(frame -> byTimestamp.put(
                        frame.timestampMs(), frame));
        return List.copyOf(byTimestamp.values());
    }

    private long resolveWindowEnd(List<LoudnessFrame> frames, int index,
                                  long audioDurationMs) {
        long startMs = frames.get(index).timestampMs();
        if (index + 1 < frames.size()) {
            return Math.min(audioDurationMs,
                    Math.max(startMs, frames.get(index + 1).timestampMs()));
        }
        if (index > 0) {
            long previous = frames.get(index - 1).timestampMs();
            long interval = Math.max(0, startMs - previous);
            return Math.min(audioDurationMs, startMs + interval);
        }
        return startMs;
    }
}
