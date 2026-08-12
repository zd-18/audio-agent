package com.audioagent.analysis.volume;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VolumeIssueSegmentMerger {

    private final AnalysisProperties properties;

    public List<VolumeIssueSegment> merge(
            List<VolumeSampleWindow> windows,
            long audioDurationMs
    ) {
        if (windows.isEmpty() || audioDurationMs <= 0) {
            return List.of();
        }
        AnalysisProperties.VolumeSegment config =
                properties.getVolumeSegment();
        List<VolumeSampleWindow> sorted = windows.stream()
                .filter(window -> window.startMs() >= 0
                        && window.endMs() > window.startMs()
                        && window.startMs() < audioDurationMs)
                .map(window -> clamp(window, audioDurationMs))
                .sorted(Comparator.comparingLong(
                        VolumeSampleWindow::startMs))
                .toList();

        List<VolumeIssueSegment> result = new ArrayList<>();
        MutableSegment current = null;
        for (VolumeSampleWindow window : sorted) {
            if (current == null) {
                current = new MutableSegment(window);
                continue;
            }
            long gap = Math.max(0, window.startMs() - current.endMs);
            if (window.issueType() == current.issueType
                    && (gap == 0 || gap < config.getMergeGapMs())) {
                current.add(window);
            } else {
                addIfLongEnough(result, current, config);
                current = new MutableSegment(window);
            }
        }
        addIfLongEnough(result, current, config);
        return List.copyOf(result);
    }

    private VolumeSampleWindow clamp(VolumeSampleWindow window,
                                     long audioDurationMs) {
        return new VolumeSampleWindow(window.issueType(),
                window.startMs(),
                Math.min(window.endMs(), audioDurationMs),
                window.momentaryLufs());
    }

    private void addIfLongEnough(
            List<VolumeIssueSegment> result,
            MutableSegment segment,
            AnalysisProperties.VolumeSegment config
    ) {
        if (segment == null) {
            return;
        }
        VolumeIssueSegment immutable = segment.toImmutable();
        if (immutable.durationMs() >= config.getMinDurationMs()) {
            result.add(immutable);
        }
    }

    private static final class MutableSegment {

        private final VolumeIssueType issueType;
        private final long startMs;
        private long endMs;
        private final List<VolumeSampleWindow> samples =
                new ArrayList<>();

        private MutableSegment(VolumeSampleWindow first) {
            issueType = first.issueType();
            startMs = first.startMs();
            endMs = first.endMs();
            samples.add(first);
        }

        private void add(VolumeSampleWindow window) {
            endMs = Math.max(endMs, window.endMs());
            samples.add(window);
        }

        private VolumeIssueSegment toImmutable() {
            return new VolumeIssueSegment(issueType, startMs, endMs,
                    samples);
        }
    }
}
