package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.noise.NoiseOverlapRange;
import com.audioagent.analysis.noise.NoiseRiskOverlapFilter;
import com.audioagent.analysis.noise.NoiseRiskSegment;
import com.audioagent.analysis.service.AudioIssueSegmentService;
import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.analysis.volume.VolumeIssueSegment;
import com.audioagent.analysis.volume.VolumeIssueSeverityEvaluator;
import com.audioagent.analysis.volume.VolumeIssueSilenceFilter;
import com.audioagent.analysis.volume.VolumeIssueType;
import com.audioagent.analysis.vo.IssueSegmentVO;
import com.audioagent.analysis.vo.IssueSummaryVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AudioIssueSegmentServiceImpl
        implements AudioIssueSegmentService {

    private static final String SILENCE = "SILENCE";
    private static final String VOLUME_DROP = "VOLUME_DROP";
    private static final String VOLUME_SPIKE = "VOLUME_SPIKE";
    private static final String NOISE_RISK = "NOISE_RISK";
    private static final String DESCRIPTION =
            "Detected continuous silence segment";

    private final AudioIssueSegmentMapper issueSegmentMapper;
    private final AudioAnalysisTaskMapper taskMapper;
    private final AnalysisProperties analysisProperties;
    private final ObjectMapper objectMapper;
    private final VolumeIssueSilenceFilter volumeIssueSilenceFilter;
    private final VolumeIssueSeverityEvaluator volumeSeverityEvaluator;
    private final NoiseRiskOverlapFilter noiseRiskOverlapFilter;
    private final AudioResourceOwnershipService ownershipService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IssueStatistics replaceSilenceSegments(
            Long taskId, Long audioFileId,
            List<SilenceSegment> segments) {
        AnalysisProperties.Silence config =
                analysisProperties.getSilence();
        String metricJson = buildMetricJson(config);
        LocalDateTime now = LocalDateTime.now();

        List<AudioIssueSegment> records = segments.stream()
                .map(segment -> toEntity(taskId, audioFileId, segment,
                        metricJson, now, config))
                .toList();

        issueSegmentMapper.deleteByTaskAndType(taskId, SILENCE);
        if (!records.isEmpty()) {
            int inserted = issueSegmentMapper.insertBatch(records);
            if (inserted != records.size()) {
                throw new IllegalStateException(
                        "Not all issue segments were persisted");
            }
        }

        long totalDuration = records.stream()
                .mapToLong(AudioIssueSegment::getDurationMs)
                .sum();
        return new IssueStatistics(records.size(), records.size(),
                totalDuration);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueSummaryVO getIssues(Long userId, Long taskId,
                                    String issueType) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "taskId must be greater than 0");
        }
        ownershipService.requireTaskOwned(userId, taskId);
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "Audio analysis task does not exist");
        }

        String normalizedType = normalizeIssueType(issueType);
        List<AudioIssueSegment> allSegments = issueSegmentMapper
                .selectByTask(taskId).stream()
                .sorted(Comparator.comparingLong(
                        AudioIssueSegment::getStartMs))
                .toList();
        List<AudioIssueSegment> segments = normalizedType == null
                ? allSegments
                : allSegments.stream()
                .filter(segment -> normalizedType.equals(
                        segment.getIssueType()))
                .toList();
        long totalDuration = segments.stream()
                .mapToLong(AudioIssueSegment::getDurationMs)
                .sum();
        int volumeDropCount = (int) allSegments.stream()
                .filter(segment -> VOLUME_DROP.equals(
                        segment.getIssueType()))
                .count();
        int volumeSpikeCount = (int) allSegments.stream()
                .filter(segment -> VOLUME_SPIKE.equals(
                        segment.getIssueType()))
                .count();
        long totalVolumeDuration = allSegments.stream()
                .filter(segment -> VOLUME_DROP.equals(segment.getIssueType())
                        || VOLUME_SPIKE.equals(segment.getIssueType()))
                .mapToLong(AudioIssueSegment::getDurationMs)
                .sum();
        int noiseRiskCount = (int) allSegments.stream()
                .filter(segment -> NOISE_RISK.equals(segment.getIssueType()))
                .count();
        long totalNoiseRiskDuration = allSegments.stream()
                .filter(segment -> NOISE_RISK.equals(segment.getIssueType()))
                .mapToLong(AudioIssueSegment::getDurationMs)
                .sum();

        return IssueSummaryVO.builder()
                .taskId(taskId)
                .audioFileId(task.getAudioFileId())
                .issueCount(segments.size())
                .totalIssueDurationMs(totalDuration)
                .volumeIssueCount(volumeDropCount + volumeSpikeCount)
                .volumeDropCount(volumeDropCount)
                .volumeSpikeCount(volumeSpikeCount)
                .totalVolumeIssueDurationMs(totalVolumeDuration)
                .noiseRiskCount(noiseRiskCount)
                .totalNoiseRiskDurationMs(totalNoiseRiskDuration)
                .records(segments.stream()
                        .map(segment -> IssueSegmentVO.from(
                                segment, objectMapper))
                        .toList())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VolumeIssueStatistics replaceVolumeSegments(
            Long taskId, Long audioFileId,
            List<VolumeIssueSegment> candidates,
            BigDecimal baselineIntegratedLufs,
            long audioDurationMs
    ) {
        if (baselineIntegratedLufs == null) {
            candidates = List.of();
        }
        List<SilenceSegment> silenceSegments = issueSegmentMapper
                .selectByTaskAndType(taskId, SILENCE).stream()
                .map(entity -> new SilenceSegment(entity.getStartMs(),
                        entity.getEndMs(), entity.getDurationMs()))
                .toList();
        List<VolumeIssueSegment> filtered = volumeIssueSilenceFilter
                .excludeSilence(candidates, silenceSegments,
                        audioDurationMs);
        LocalDateTime now = LocalDateTime.now();
        List<AudioIssueSegment> records = filtered.stream()
                .map(segment -> toVolumeEntity(taskId, audioFileId,
                        segment, baselineIntegratedLufs, now))
                .toList();

        issueSegmentMapper.deleteVolumeTypesByTask(taskId);
        if (!records.isEmpty()) {
            int inserted = issueSegmentMapper.insertBatch(records);
            if (inserted != records.size()) {
                throw new IllegalStateException(
                        "Not all volume issue segments were persisted");
            }
        }

        int dropCount = (int) records.stream()
                .filter(record -> VOLUME_DROP.equals(record.getIssueType()))
                .count();
        int spikeCount = records.size() - dropCount;
        long totalDuration = records.stream()
                .mapToLong(AudioIssueSegment::getDurationMs)
                .sum();
        return new VolumeIssueStatistics(records.size(), dropCount,
                spikeCount, totalDuration);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoiseIssueStatistics replaceNoiseRiskSegments(
            Long taskId, Long audioFileId,
            List<NoiseRiskSegment> candidates) {
        List<NoiseOverlapRange> silence = loadRanges(taskId, SILENCE);
        List<NoiseOverlapRange> spikes = loadRanges(taskId, VOLUME_SPIKE);
        List<NoiseRiskSegment> filtered = noiseRiskOverlapFilter.filter(
                candidates == null ? List.of() : candidates,
                silence, spikes);
        LocalDateTime now = LocalDateTime.now();
        List<AudioIssueSegment> records = filtered.stream()
                .map(segment -> toNoiseEntity(taskId, audioFileId,
                        segment, now))
                .toList();

        issueSegmentMapper.deleteByTaskAndType(taskId, NOISE_RISK);
        if (!records.isEmpty()) {
            int inserted = issueSegmentMapper.insertBatch(records);
            if (inserted != records.size()) {
                throw new IllegalStateException(
                        "Not all noise risk segments were persisted");
            }
        }
        long totalDuration = records.stream()
                .mapToLong(AudioIssueSegment::getDurationMs).sum();
        return new NoiseIssueStatistics(records.size(), totalDuration);
    }

    private List<NoiseOverlapRange> loadRanges(Long taskId,
                                               String issueType) {
        return issueSegmentMapper.selectByTaskAndType(taskId, issueType)
                .stream().map(entity -> new NoiseOverlapRange(
                        entity.getStartMs(), entity.getEndMs())).toList();
    }

    private AudioIssueSegment toEntity(
            Long taskId, Long audioFileId, SilenceSegment segment,
            String metricJson, LocalDateTime now,
            AnalysisProperties.Silence config) {
        AudioIssueSegment entity = new AudioIssueSegment();
        entity.setId(IdWorker.getId());
        entity.setTaskId(taskId);
        entity.setAudioFileId(audioFileId);
        entity.setIssueType(SILENCE);
        entity.setStartMs(segment.startMs());
        entity.setEndMs(segment.endMs());
        entity.setDurationMs(segment.durationMs());
        entity.setSeverity(severityOf(segment.durationMs(), config));
        entity.setMetricJson(metricJson);
        entity.setDescription(DESCRIPTION);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private AudioIssueSegment toVolumeEntity(
            Long taskId, Long audioFileId,
            VolumeIssueSegment segment,
            BigDecimal baselineIntegratedLufs,
            LocalDateTime now
    ) {
        AudioIssueSegment entity = new AudioIssueSegment();
        entity.setId(IdWorker.getId());
        entity.setTaskId(taskId);
        entity.setAudioFileId(audioFileId);
        entity.setIssueType(segment.issueType().name());
        entity.setStartMs(segment.startMs());
        entity.setEndMs(segment.endMs());
        entity.setDurationMs(segment.durationMs());
        entity.setSeverity(volumeSeverityEvaluator.evaluate(segment,
                baselineIntegratedLufs));
        entity.setMetricJson(buildVolumeMetricJson(segment,
                baselineIntegratedLufs));
        entity.setDescription(segment.issueType()
                == VolumeIssueType.VOLUME_DROP
                ? "该片段音量明显低于整段音频平均水平。"
                : "该片段音量明显高于整段音频平均水平，"
                + "建议检查是否存在突发大声。");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private AudioIssueSegment toNoiseEntity(
            Long taskId, Long audioFileId,
            NoiseRiskSegment segment, LocalDateTime now) {
        AudioIssueSegment entity = new AudioIssueSegment();
        entity.setId(IdWorker.getId());
        entity.setTaskId(taskId);
        entity.setAudioFileId(audioFileId);
        entity.setIssueType(NOISE_RISK);
        entity.setStartMs(segment.startMs());
        entity.setEndMs(segment.endMs());
        entity.setDurationMs(segment.durationMs());
        entity.setSeverity(segment.severity());
        entity.setMetricJson(buildNoiseMetricJson(segment));
        entity.setDescription(switch (segment.severity()) {
            case "HIGH" -> "该片段噪声风险较高，建议优先检查并进行降噪处理。";
            case "MEDIUM" -> "该片段疑似存在持续背景噪声，可能影响听感。";
            default -> "该片段存在轻微背景噪声风险，建议播放确认。";
        });
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    String severityOf(long durationMs,
                      AnalysisProperties.Silence config) {
        if (durationMs >= config.getHighDurationMs()) {
            return "HIGH";
        }
        if (durationMs >= config.getMediumDurationMs()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildMetricJson(AnalysisProperties.Silence config) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("noiseThresholdDb", config.getNoiseThresholdDb());
        metrics.put("minDurationMs", config.getMinDurationMs());
        metrics.put("detector", "FFMPEG_SILENCEDETECT");
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize silence detector metrics", e);
        }
    }

    private String buildVolumeMetricJson(
            VolumeIssueSegment segment,
            BigDecimal baselineIntegratedLufs
    ) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("baselineIntegratedLufs",
                scaleMetric(baselineIntegratedLufs));
        metrics.put("averageMomentaryLufs",
                scaleMetric(segment.averageMomentaryLufs()));
        metrics.put("minimumMomentaryLufs",
                scaleMetric(segment.minimumMomentaryLufs()));
        metrics.put("maximumMomentaryLufs",
                scaleMetric(segment.maximumMomentaryLufs()));
        metrics.put("deviationLu", scaleMetric(
                segment.deviationLu(baselineIntegratedLufs)));
        metrics.put("sampleCount", segment.sampleCount());
        metrics.put("detector", "EBUR128_MOMENTARY");
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize volume issue metrics", e);
        }
    }

    private String buildNoiseMetricJson(NoiseRiskSegment segment) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        putIfPresent(metrics, "averageRmsDbfs", segment.averageRmsDbfs());
        putIfPresent(metrics, "averageSpectralFlatness",
                segment.averageSpectralFlatness());
        putIfPresent(metrics, "averageSpectralEntropy",
                segment.averageSpectralEntropy());
        putIfPresent(metrics, "rmsVariationDb", segment.rmsVariationDb());
        putIfPresent(metrics, "noiseRiskScore", segment.noiseRiskScore());
        putIfPresent(metrics, "maximumNoiseRiskScore",
                segment.maximumNoiseRiskScore());
        metrics.put("confidence", segment.confidence());
        metrics.put("sampleCount", segment.sampleCount());
        metrics.put("detector", "FFMPEG_SPECTRAL_HEURISTIC");
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize noise risk metrics", e);
        }
    }

    private void putIfPresent(Map<String, Object> metrics, String key,
                              BigDecimal value) {
        if (value != null) {
            metrics.put(key, value);
        }
    }

    private BigDecimal scaleMetric(BigDecimal value) {
        return value == null ? null : value.setScale(2,
                RoundingMode.HALF_UP);
    }

    private String normalizeIssueType(String issueType) {
        if (!StringUtils.hasText(issueType)) {
            return null;
        }
        String normalized = issueType.trim().toUpperCase(Locale.ROOT);
        if (!SILENCE.equals(normalized)
                && !VOLUME_DROP.equals(normalized)
                && !VOLUME_SPIKE.equals(normalized)
                && !NOISE_RISK.equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "Unsupported issue type: " + issueType);
        }
        return normalized;
    }
}
