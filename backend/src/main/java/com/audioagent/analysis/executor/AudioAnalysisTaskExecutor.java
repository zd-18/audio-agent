package com.audioagent.analysis.executor;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.loudness.LoudnessAnalyzer;
import com.audioagent.analysis.loudness.LoudnessAnalysis;
import com.audioagent.analysis.loudness.LoudnessEvaluation;
import com.audioagent.analysis.loudness.LoudnessEvaluator;
import com.audioagent.analysis.loudness.LoudnessMetrics;
import com.audioagent.analysis.noise.NoiseRiskAnalyzer;
import com.audioagent.analysis.noise.NoiseRiskSegment;
import com.audioagent.analysis.probe.AudioMetadata;
import com.audioagent.analysis.probe.AudioMetadataProbe;
import com.audioagent.analysis.service.AudioIssueSegmentService;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.service.AudioIssueSegmentService.IssueStatistics;
import com.audioagent.analysis.service.AudioIssueSegmentService.VolumeIssueStatistics;
import com.audioagent.analysis.service.AudioIssueSegmentService.NoiseIssueStatistics;
import com.audioagent.analysis.silence.SilenceDetector;
import com.audioagent.analysis.silence.SilenceSegment;
import com.audioagent.analysis.volume.VolumeIssueAnalyzer;
import com.audioagent.analysis.volume.VolumeIssueSegment;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 音频分析任务执行器。
 *
 * <p>成功时内部保存结果并更新 SUCCESS；
 * 失败时向上层抛出 {@link AudioAnalysisException}，
 * 由调用方（消费者 / local 调度器）决定是否重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioAnalysisTaskExecutor {

    private final AudioAnalysisTaskMapper taskMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisResultMapper resultMapper;
    private final MinioStorageService minioStorageService;
    private final AudioMetadataProbe audioMetadataProbe;
    private final SilenceDetector silenceDetector;
    private final LoudnessAnalyzer loudnessAnalyzer;
    private final LoudnessEvaluator loudnessEvaluator;
    private final VolumeIssueAnalyzer volumeIssueAnalyzer;
    private final NoiseRiskAnalyzer noiseRiskAnalyzer;
    private final AudioIssueSegmentService issueSegmentService;
    private final AudioAnalysisReportService reportService;
    private final AudioProcessingPlanService processingPlanService;
    private final AnalysisProperties analysisProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行一次完整的音频分析。
     *
     * @throws AudioAnalysisException 分析失败时抛出
     */
    public void execute(Long taskId, Long audioFileId) {
        executeNewClaim(taskId, audioFileId, null);
    }

    /**
     * Rabbit consumer 使用带 fencing 的执行令牌抢占并执行任务。
     */
    public void execute(Long taskId, Long audioFileId,
                        String executionToken) {
        executeNewClaim(taskId, audioFileId, executionToken);
    }

    /**
     * stale PROCESSING 已由 listener 通过 CAS 转移执行权后继续执行。
     */
    public void executeClaimed(Long taskId, Long audioFileId,
                               String executionToken) {
        runClaimedTask(taskId, audioFileId, executionToken);
    }

    private void executeNewClaim(Long taskId, Long audioFileId,
                                 String executionToken) {
        boolean claimed = Boolean.TRUE.equals(
                transactionTemplate.execute(
                        s -> claimTask(taskId, executionToken))
        );

        if (!claimed) {
            log.info(
                    "Task already claimed or not in PENDING state, taskId={}",
                    taskId
            );
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED,
                    false,
                    "Task already claimed or not in PENDING state"
            );
        }

        runClaimedTask(taskId, audioFileId, executionToken);
    }

    private void runClaimedTask(Long taskId, Long audioFileId,
                                String executionToken) {

        log.info(
                "Task claimed, starting analysis, taskId={}, audioFileId={}",
                taskId,
                audioFileId
        );

        long startTime = System.currentTimeMillis();
        Path tempFile = null;

        try {
            AudioFile audioFile = lookupFile(audioFileId);
            tempFile = downloadToTempFile(audioFile);
            renewProcessingLease(taskId, executionToken);
            log.debug("File downloaded from MinIO, taskId={}", taskId);

            long ffprobeStart = System.currentTimeMillis();
            AudioMetadata metadata = runProbe(tempFile);
            renewProcessingLease(taskId, executionToken);
            long ffprobeElapsed = System.currentTimeMillis()
                    - ffprobeStart;
            log.info(
                    "FFprobe completed, taskId={}, elapsedMs={}",
                    taskId,
                    ffprobeElapsed
            );

            long silenceStartedAt = System.currentTimeMillis();
            long audioDurationMs = metadata.getDurationMs() == null
                    ? -1L : metadata.getDurationMs();
            List<SilenceSegment> silenceSegments =
                    silenceDetector.detect(tempFile, audioDurationMs);
            renewProcessingLease(taskId, executionToken);
            long silenceElapsed = System.currentTimeMillis()
                    - silenceStartedAt;
            long detectedSilenceDuration = silenceSegments.stream()
                    .mapToLong(SilenceSegment::durationMs)
                    .sum();
            log.info(
                    "Silence detection completed, taskId={}, audioFileId={}, "
                            + "elapsedMs={}, count={}, totalDurationMs={}, "
                            + "thresholdDb={}, minDurationMs={}",
                    taskId, audioFileId, silenceElapsed,
                    silenceSegments.size(), detectedSilenceDuration,
                    analysisProperties.getSilence().getNoiseThresholdDb(),
                    analysisProperties.getSilence().getMinDurationMs()
            );

            long loudnessStartedAt = System.currentTimeMillis();
            LoudnessAnalysis loudnessAnalysis = loudnessAnalyzer
                    .analyze(tempFile).orElse(null);
            renewProcessingLease(taskId, executionToken);
            LoudnessMetrics loudness = loudnessAnalysis == null
                    || !analysisProperties.getLoudness().isEnabled()
                    ? null : loudnessAnalysis.metrics();
            long loudnessElapsed = System.currentTimeMillis()
                    - loudnessStartedAt;
            logLoudnessResult(taskId, audioFileId, loudness,
                    loudnessElapsed);

            List<VolumeIssueSegment> volumeCandidates =
                    loudnessAnalysis == null
                            || !analysisProperties.getVolumeSegment()
                            .isEnabled()
                            ? List.of()
                            : runVolumeIssueAnalysis(loudnessAnalysis,
                            audioDurationMs);
            log.info("Volume segment candidates prepared, taskId={}, "
                            + "audioFileId={}, count={}",
                    taskId, audioFileId, volumeCandidates.size());

            List<NoiseRiskSegment> noiseCandidates = loudnessAnalysis == null
                    || !analysisProperties.getNoiseRisk().isEnabled()
                    ? List.of()
                    : noiseRiskAnalyzer.analyze(
                            loudnessAnalysis.noiseFrames(), audioDurationMs);
            log.info("Noise risk candidates prepared, taskId={}, "
                            + "audioFileId={}, count={}",
                    taskId, audioFileId, noiseCandidates.size());

            transactionTemplate.execute(s -> {
                renewProcessingLease(taskId, executionToken);
                IssueStatistics statistics = issueSegmentService
                        .replaceSilenceSegments(taskId, audioFileId,
                                silenceSegments);
                VolumeIssueStatistics volumeStatistics =
                        issueSegmentService.replaceVolumeSegments(
                                taskId, audioFileId, volumeCandidates,
                                loudness == null ? null
                                        : loudness
                                        .integratedLoudnessLufs(),
                                audioDurationMs);
                NoiseIssueStatistics noiseStatistics = issueSegmentService
                        .replaceNoiseRiskSegments(taskId, audioFileId,
                                noiseCandidates);
                saveResult(taskId, audioFileId, metadata, statistics,
                        volumeStatistics, noiseStatistics, loudness);
                generateReport(taskId, audioFileId);
                updateTaskSuccess(taskId, executionToken);
                return null;
            });

            generateDefaultProcessingPlan(taskId, audioFileId,
                    audioFile.getUserId());

            long totalElapsed = System.currentTimeMillis() - startTime;
            log.info(
                    "Analysis completed successfully, taskId={}, totalElapsedMs={}",
                    taskId,
                    totalElapsed
            );

        } catch (AudioAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw mapException(e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private boolean claimTask(Long taskId, String executionToken) {
        LambdaUpdateWrapper<AudioAnalysisTask> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(AudioAnalysisTask::getId, taskId)
                .eq(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PENDING)
                .set(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PROCESSING)
                .set(AudioAnalysisTask::getProgress, 10)
                .set(AudioAnalysisTask::getStartedAt,
                        LocalDateTime.now())
                .set(AudioAnalysisTask::getUpdatedAt,
                        LocalDateTime.now());
        if (executionToken != null) {
            wrapper.set(AudioAnalysisTask::getLastMessageId,
                    executionToken);
        }

        int rows = taskMapper.update(null, wrapper);
        if (rows != 1) {
            log.warn("Claim task failed, taskId={}, rows={}", taskId, rows);
            return false;
        }
        return true;
    }

    private void renewProcessingLease(Long taskId, String executionToken) {
        if (executionToken == null) {
            return;
        }
        LambdaUpdateWrapper<AudioAnalysisTask> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(AudioAnalysisTask::getId, taskId)
                .eq(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PROCESSING)
                .eq(AudioAnalysisTask::getLastMessageId, executionToken)
                .set(AudioAnalysisTask::getUpdatedAt,
                        LocalDateTime.now());
        if (taskMapper.update(null, wrapper) != 1) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED,
                    false,
                    "Task execution lease is no longer owned by this consumer"
            );
        }
    }

    private AudioFile lookupFile(Long audioFileId) {
        AudioFile audioFile = audioFileMapper.selectById(audioFileId);
        if (audioFile == null
                || Integer.valueOf(1).equals(audioFile.getDeleted())) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.AUDIO_FILE_NOT_FOUND,
                    false,
                    "音频文件不存在"
            );
        }
        return audioFile;
    }

    private AudioMetadata runProbe(Path tempFile) {
        try {
            return audioMetadataProbe.probe(tempFile);
        } catch (RuntimeException e) {
            throw mapProbeException(e);
        }
    }

    private AudioAnalysisException mapProbeException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                    false, "音频分析失败", e);
        }

        if (msg.contains("程序不存在")
                || msg.contains("Cannot run program")) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.FFPROBE_NOT_FOUND,
                    false, "ffprobe 程序不存在或无法启动", e);
        }
        if (msg.contains("超时") || msg.contains("timed out")) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.FFPROBE_TIMEOUT,
                    true, "ffprobe 执行超时", e);
        }
        if (msg.contains("未检测到音频流")) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.NO_AUDIO_STREAM,
                    false, "未检测到音频流", e);
        }
        if (msg.contains("解析失败")) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .FFPROBE_RESULT_PARSE_ERROR,
                    false, "ffprobe 返回结果解析失败", e);
        }
        if (msg.startsWith("ffprobe 执行失败")) {
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.NO_AUDIO_STREAM,
                    false, truncate(msg, 500), e);
        }

        return new AudioAnalysisException(
                AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                false, "分析失败: " + truncate(msg, 490), e);
    }

    private AudioAnalysisException mapException(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.PROCESS_INTERRUPTED,
                    true, "分析被中断", e);
        }
        if (e instanceof IOException) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            boolean retryable = msg.contains("Connection")
                    || msg.contains("timeout")
                    || msg.contains("reset");
            return new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.TEMPORARY_IO_ERROR,
                    retryable, "IO 异常: " + truncate(msg, 480), e);
        }
        return new AudioAnalysisException(
                AudioAnalysisException.ErrorCodes.INVALID_AUDIO_FILE,
                false,
                "分析失败: " + truncate(
                        e.getMessage() != null ? e.getMessage() : "未知错误",
                        490),
                e);
    }

    private void saveResult(Long taskId, Long audioFileId,
                            AudioMetadata metadata,
                            IssueStatistics statistics,
                            VolumeIssueStatistics volumeStatistics,
                            NoiseIssueStatistics noiseStatistics,
                            LoudnessMetrics loudness) {
        LocalDateTime now = LocalDateTime.now();

        AudioAnalysisResult result = new AudioAnalysisResult();
        result.setId(IdWorker.getId());
        result.setTaskId(taskId);
        result.setAudioFileId(audioFileId);
        result.setFormatName(metadata.getFormatName());
        result.setCodecName(metadata.getCodecName());
        result.setDurationMs(metadata.getDurationMs());
        result.setSampleRate(metadata.getSampleRate());
        result.setChannels(metadata.getChannels());
        result.setBitRate(metadata.getBitRate());
        result.setFileSize(metadata.getFileSize());
        result.setIssueCount(statistics.issueCount()
                + volumeStatistics.issueCount()
                + noiseStatistics.issueCount());
        result.setSilenceCount(statistics.silenceCount());
        result.setTotalSilenceDurationMs(
                statistics.totalSilenceDurationMs());
        result.setSilenceRatio(calculateSilenceRatio(
                statistics.totalSilenceDurationMs(),
                metadata.getDurationMs()));
        if (loudness != null) {
            result.setIntegratedLoudnessLufs(scaleForStorage(
                    loudness.integratedLoudnessLufs()));
            result.setLoudnessRangeLu(scaleForStorage(
                    loudness.loudnessRangeLu()));
            result.setSamplePeakDbfs(scaleForStorage(
                    loudness.samplePeakDbfs()));
            result.setTruePeakDbfs(scaleForStorage(
                    loudness.truePeakDbfs()));
        }
        result.setCreatedAt(now);
        result.setUpdatedAt(now);

        int rows = resultMapper.upsert(result);
        if (rows < 1) {
            throw new IllegalStateException(
                    "Analysis result was not persisted");
        }
        log.debug("Analysis result saved, taskId={}, resultId={}",
                taskId, result.getId());
    }

    private void generateReport(Long taskId, Long audioFileId) {
        try {
            reportService.generateAndSave(taskId, audioFileId);
        } catch (RuntimeException e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .REPORT_GENERATION_FAILED,
                    false, "统一分析报告生成失败", e);
        }
    }

    private void generateDefaultProcessingPlan(Long taskId,
                                               Long audioFileId,
                                               Long userId) {
        if (!analysisProperties.getProcessingPlan().isEnabled()) {
            log.info("Automatic processing plan generation disabled, "
                    + "taskId={}, audioFileId={}", taskId, audioFileId);
            return;
        }
        try {
            processingPlanService.generateForOwner(userId, taskId);
        } catch (RuntimeException e) {
            log.warn("Automatic processing plan generation failed without "
                            + "affecting analysis success, taskId={}, "
                            + "audioFileId={}",
                    taskId, audioFileId, e);
        }
    }

    private BigDecimal calculateSilenceRatio(long totalSilenceDurationMs,
                                             Long audioDurationMs) {
        if (totalSilenceDurationMs <= 0
                || audioDurationMs == null || audioDurationMs <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalSilenceDurationMs)
                .divide(BigDecimal.valueOf(audioDurationMs), 8,
                        RoundingMode.HALF_UP);
    }

    private BigDecimal scaleForStorage(BigDecimal value) {
        return value == null ? null : value.setScale(2,
                RoundingMode.HALF_UP);
    }

    private List<VolumeIssueSegment> runVolumeIssueAnalysis(
            LoudnessAnalysis loudnessAnalysis,
            long audioDurationMs
    ) {
        try {
            return volumeIssueAnalyzer.analyze(loudnessAnalysis,
                    audioDurationMs);
        } catch (AudioAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes
                            .VOLUME_SEGMENT_ANALYSIS_FAILED,
                    false, "Volume segment analysis failed", e);
        }
    }

    private void logLoudnessResult(Long taskId, Long audioFileId,
                                   LoudnessMetrics loudness,
                                   long elapsedMs) {
        if (loudness == null) {
            log.info("Loudness analysis skipped, taskId={}, "
                            + "audioFileId={}, elapsedMs={}",
                    taskId, audioFileId, elapsedMs);
            return;
        }
        LoudnessEvaluation evaluation = loudnessEvaluator.evaluate(loudness);
        log.info("Loudness analysis completed, taskId={}, audioFileId={}, "
                        + "elapsedMs={}, integratedLufs={}, lraLu={}, "
                        + "truePeakDbfs={}, loudnessLevel={}, peakRisk={}, "
                        + "dynamicRangeLevel={}",
                taskId, audioFileId, elapsedMs,
                loudness.integratedLoudnessLufs(),
                loudness.loudnessRangeLu(), loudness.truePeakDbfs(),
                evaluation.loudnessLevel(), evaluation.peakRisk(),
                evaluation.dynamicRangeLevel());
    }

    private void updateTaskSuccess(Long taskId, String executionToken) {
        LambdaUpdateWrapper<AudioAnalysisTask> wrapper =
                new LambdaUpdateWrapper<>();
        wrapper.eq(AudioAnalysisTask::getId, taskId)
                .eq(executionToken != null,
                        AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.PROCESSING)
                .eq(executionToken != null,
                        AudioAnalysisTask::getLastMessageId,
                        executionToken)
                .set(AudioAnalysisTask::getStatus,
                        AnalysisTaskStatus.SUCCESS)
                .set(AudioAnalysisTask::getProgress, 100)
                .set(AudioAnalysisTask::getErrorMessage, null)
                .set(AudioAnalysisTask::getLastErrorCode, null)
                .set(AudioAnalysisTask::getNextRetryAt, null)
                .set(AudioAnalysisTask::getFinishedAt,
                        LocalDateTime.now())
                .set(AudioAnalysisTask::getUpdatedAt,
                        LocalDateTime.now());

        int rows = taskMapper.update(null, wrapper);
        if (rows != 1) {
            throw new AudioAnalysisException(
                    AudioAnalysisException.ErrorCodes.ALREADY_CLAIMED,
                    false,
                    "Task execution lease was lost before success commit"
            );
        }
    }

    private Path downloadToTempFile(AudioFile audioFile)
            throws IOException {
        String extension = audioFile.getExtension() != null
                ? "." + audioFile.getExtension()
                : ".tmp";

        Path tempFile = Files.createTempFile(
                "audio-analysis-", extension);

        try (InputStream is = minioStorageService.getObject(
                audioFile.getObjectKey())) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary analysis file, name={}",
                        tempFile.getFileName(), e);
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen
                ? s.substring(0, maxLen - 3) + "..."
                : s;
    }
}
