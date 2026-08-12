package com.audioagent.analysis.volume;

import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VolumeIssueSilenceFilter {

    private final AnalysisProperties properties;

    public List<VolumeIssueSegment> excludeSilence(
            List<VolumeIssueSegment> candidates,
            List<SilenceSegment> silenceSegments,
            long audioDurationMs
    ) {
        AnalysisProperties.VolumeSegment config =
                properties.getVolumeSegment();
        List<SilenceSegment> silence = mergeSilence(
                silenceSegments, audioDurationMs);
        if (silence.isEmpty()) {
            return candidates;
        }

        List<VolumeIssueSegment> result = new ArrayList<>();
        for (VolumeIssueSegment candidate : candidates) {
            if (candidate.issueType() != VolumeIssueType.VOLUME_DROP) {
                result.add(candidate);
                continue;
            }
            long duration = candidate.durationMs();
            if (duration <= 0) {
                continue;
            }
            long overlap = silence.stream()
                    .mapToLong(item -> overlap(candidate.startMs(),
                            candidate.endMs(), item.startMs(), item.endMs()))
                    .sum();
            BigDecimal overlapRatio = BigDecimal.valueOf(overlap)
                    .divide(BigDecimal.valueOf(duration), 8,
                            RoundingMode.HALF_UP);
            if (overlapRatio.compareTo(
                    config.getSilenceOverlapRatio()) >= 0) {
                continue;
            }
            for (Range piece : subtractSilence(candidate, silence)) {
                VolumeIssueSegment trimmed = trim(candidate, piece);
                if (trimmed != null && trimmed.durationMs()
                        >= config.getMinDurationMs()) {
                    result.add(trimmed);
                }
            }
        }
        return result.stream()
                .sorted(Comparator.comparingLong(
                        VolumeIssueSegment::startMs))
                .toList();
    }

    private List<SilenceSegment> mergeSilence(
            List<SilenceSegment> input,
            long audioDurationMs
    ) {
        List<SilenceSegment> sorted = input.stream()
                .map(item -> silenceSegment(
                        Math.max(0, item.startMs()),
                        Math.min(audioDurationMs, item.endMs())))
                .filter(item -> item.endMs() > item.startMs())
                .sorted(Comparator.comparingLong(SilenceSegment::startMs))
                .toList();
        List<SilenceSegment> merged = new ArrayList<>();
        for (SilenceSegment item : sorted) {
            if (merged.isEmpty()) {
                merged.add(item);
                continue;
            }
            SilenceSegment last = merged.getLast();
            if (item.startMs() <= last.endMs()) {
                merged.set(merged.size() - 1, silenceSegment(
                        last.startMs(), Math.max(last.endMs(), item.endMs())));
            } else {
                merged.add(item);
            }
        }
        return merged;
    }

    private SilenceSegment silenceSegment(long startMs, long endMs) {
        return new SilenceSegment(startMs, endMs,
                Math.max(0, endMs - startMs));
    }

    private List<Range> subtractSilence(
            VolumeIssueSegment candidate,
            List<SilenceSegment> silence
    ) {
        List<Range> pieces = new ArrayList<>();
        long cursor = candidate.startMs();
        for (SilenceSegment item : silence) {
            if (item.endMs() <= cursor
                    || item.startMs() >= candidate.endMs()) {
                continue;
            }
            if (item.startMs() > cursor) {
                pieces.add(new Range(cursor,
                        Math.min(item.startMs(), candidate.endMs())));
            }
            cursor = Math.max(cursor, item.endMs());
            if (cursor >= candidate.endMs()) {
                break;
            }
        }
        if (cursor < candidate.endMs()) {
            pieces.add(new Range(cursor, candidate.endMs()));
        }
        return pieces;
    }

    private VolumeIssueSegment trim(VolumeIssueSegment candidate,
                                    Range piece) {
        List<VolumeSampleWindow> samples = candidate.samples().stream()
                .filter(sample -> overlap(sample.startMs(), sample.endMs(),
                        piece.startMs(), piece.endMs()) > 0)
                .map(sample -> new VolumeSampleWindow(sample.issueType(),
                        Math.max(sample.startMs(), piece.startMs()),
                        Math.min(sample.endMs(), piece.endMs()),
                        sample.momentaryLufs()))
                .filter(sample -> sample.endMs() > sample.startMs())
                .toList();
        if (samples.isEmpty()) {
            return null;
        }
        return new VolumeIssueSegment(candidate.issueType(),
                piece.startMs(), piece.endMs(), samples);
    }

    private long overlap(long startA, long endA,
                         long startB, long endB) {
        return Math.max(0, Math.min(endA, endB)
                - Math.max(startA, startB));
    }

    private record Range(long startMs, long endMs) {
    }
}
