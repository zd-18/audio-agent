package com.audioagent.analysis.report;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.loudness.LoudnessEvaluation;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.AudioOverview;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.IssueSummary;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.LoudnessOverview;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.Payload;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.ReportIssue;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AudioAnalysisReportGenerator {

    private final AnalysisProperties properties;
    private final AudioQualityScoreCalculator scoreCalculator;
    private final ReportSummaryBuilder summaryBuilder;
    private final AudioRecommendationBuilder recommendationBuilder;
    private final LoudnessEvaluator loudnessEvaluator;

    public GeneratedReport generate(AudioFile audioFile,
                                    AudioAnalysisResult result,
                                    List<AudioIssueSegment> issues) {
        List<AudioIssueSegment> safeIssues = issues == null
                ? List.of() : issues;
        AudioQualityScoreCalculator.ScoreResult score =
                scoreCalculator.calculate(result, safeIssues);
        List<ReportIssue> timeline = safeIssues.stream()
                .sorted(Comparator.comparing(AudioIssueSegment::getStartMs,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AudioIssueSegment::getId,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder())))
                .map(this::toReportIssue)
                .toList();
        List<ReportIssue> keyIssues = safeIssues.stream()
                .sorted(ReportIssueCatalog.keyIssueComparator())
                .limit(properties.getReport().getMaxKeyIssues())
                .map(this::toReportIssue)
                .toList();
        Payload payload = Payload.builder()
                .audioOverview(buildAudioOverview(audioFile, result))
                .loudnessOverview(buildLoudnessOverview(result))
                .issueSummary(buildIssueSummary(safeIssues))
                .keyIssues(keyIssues)
                .timeline(timeline)
                .recommendations(recommendationBuilder.build(
                        result, safeIssues))
                .build();
        return new GeneratedReport(score.score(), score.grade(),
                summaryBuilder.build(safeIssues), payload);
    }

    private AudioOverview buildAudioOverview(AudioFile file,
                                             AudioAnalysisResult result) {
        return AudioOverview.builder()
                .fileName(file == null ? null : file.getOriginalName())
                .durationMs(result.getDurationMs())
                .format(result.getFormatName())
                .codec(result.getCodecName())
                .sampleRate(result.getSampleRate())
                .channels(result.getChannels())
                .build();
    }

    private LoudnessOverview buildLoudnessOverview(
            AudioAnalysisResult result) {
        if (result.getIntegratedLoudnessLufs() == null) {
            return null;
        }
        LoudnessEvaluation evaluation = loudnessEvaluator.evaluate(
                new LoudnessMetrics(result.getIntegratedLoudnessLufs(),
                        result.getLoudnessRangeLu(),
                        result.getSamplePeakDbfs(),
                        result.getTruePeakDbfs()));
        return LoudnessOverview.builder()
                .integratedLoudnessLufs(
                        result.getIntegratedLoudnessLufs())
                .loudnessRangeLu(result.getLoudnessRangeLu())
                .truePeakDbfs(result.getTruePeakDbfs())
                .loudnessLevel(evaluation.loudnessLevel())
                .peakRisk(evaluation.peakRisk())
                .dynamicRangeLevel(evaluation.dynamicRangeLevel())
                .build();
    }

    private IssueSummary buildIssueSummary(List<AudioIssueSegment> issues) {
        int silenceCount = count(issues, "SILENCE");
        int dropCount = count(issues, "VOLUME_DROP");
        int spikeCount = count(issues, "VOLUME_SPIKE");
        int noiseCount = count(issues, "NOISE_RISK");
        return IssueSummary.builder()
                .totalIssueCount(issues.size())
                .totalIssueDurationMs(duration(issues, null))
                .silenceCount(silenceCount)
                .volumeDropCount(dropCount)
                .volumeSpikeCount(spikeCount)
                .noiseRiskCount(noiseCount)
                .silenceDurationMs(duration(issues, "SILENCE"))
                .volumeIssueDurationMs(duration(issues, "VOLUME_DROP")
                        + duration(issues, "VOLUME_SPIKE"))
                .noiseRiskDurationMs(duration(issues, "NOISE_RISK"))
                .build();
    }

    private int count(List<AudioIssueSegment> issues, String type) {
        return (int) issues.stream()
                .filter(issue -> type.equals(issue.getIssueType())).count();
    }

    private long duration(List<AudioIssueSegment> issues, String type) {
        return issues.stream()
                .filter(issue -> type == null
                        || type.equals(issue.getIssueType()))
                .map(AudioIssueSegment::getDurationMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
    }

    private ReportIssue toReportIssue(AudioIssueSegment issue) {
        return ReportIssue.builder()
                .issueId(issue.getId())
                .issueType(issue.getIssueType())
                .title(ReportIssueCatalog.title(issue.getIssueType()))
                .startMs(issue.getStartMs())
                .endMs(issue.getEndMs())
                .durationMs(issue.getDurationMs())
                .severity(issue.getSeverity())
                .description(ReportIssueCatalog.description(
                        issue.getIssueType()))
                .build();
    }

    public record GeneratedReport(int qualityScore,
                                  QualityGrade qualityGrade,
                                  String summary,
                                  AudioAnalysisReportVO.Payload payload) {
    }
}
