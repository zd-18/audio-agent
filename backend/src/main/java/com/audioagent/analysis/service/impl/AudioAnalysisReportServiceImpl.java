package com.audioagent.analysis.service.impl;

import com.audioagent.analysis.entity.AudioAnalysisReport;
import com.audioagent.analysis.entity.AudioAnalysisResult;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.entity.AudioIssueSegment;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisReportMapper;
import com.audioagent.analysis.mapper.AudioAnalysisResultMapper;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioIssueSegmentMapper;
import com.audioagent.analysis.report.AudioAnalysisReportGenerator;
import com.audioagent.analysis.report.AudioAnalysisReportGenerator.GeneratedReport;
import com.audioagent.analysis.report.QualityGrade;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.analysis.vo.AudioAnalysisReportVO.Payload;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioAnalysisReportServiceImpl
        implements AudioAnalysisReportService {

    private final AudioAnalysisTaskMapper taskMapper;
    private final AudioAnalysisResultMapper resultMapper;
    private final AudioIssueSegmentMapper issueSegmentMapper;
    private final AudioAnalysisReportMapper reportMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisReportGenerator reportGenerator;
    private final AnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final AudioResourceOwnershipService ownershipService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioAnalysisReportVO getReport(Long userId, Long taskId) {
        validateTaskId(taskId);
        ownershipService.requireTaskOwned(userId, taskId);
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND,
                    "分析任务不存在");
        }
        if (task.getStatus() != AnalysisTaskStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.REPORT_NOT_READY,
                    "报告尚未生成");
        }

        AudioAnalysisReport existing = reportMapper.selectByTaskId(taskId);
        if (existing != null) {
            validateAssociation(existing, task);
            return toVO(existing);
        }
        if (!properties.getReport().isEnabled()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_READY,
                    "报告功能当前未启用");
        }

        try {
            return createSnapshot(task, task.getAudioFileId());
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Lazy report generation failed, taskId={}", taskId, e);
            throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED,
                    "报告生成失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioAnalysisReportVO generateAndSave(Long taskId,
                                                 Long audioFileId) {
        if (!properties.getReport().isEnabled()) {
            return null;
        }
        validateTaskId(taskId);
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.REPORT_DATA_INCOMPLETE,
                    "生成报告所需的任务数据不存在");
        }
        if (!task.getAudioFileId().equals(audioFileId)) {
            throw new BusinessException(ErrorCode.REPORT_DATA_INCOMPLETE,
                    "任务与音频文件关联不一致");
        }
        return createSnapshot(task, audioFileId);
    }

    private AudioAnalysisReportVO createSnapshot(AudioAnalysisTask task,
                                                 Long audioFileId) {
        long startedAt = System.currentTimeMillis();
        AudioAnalysisResult result = resultMapper.selectOne(
                new LambdaQueryWrapper<AudioAnalysisResult>()
                        .eq(AudioAnalysisResult::getTaskId, task.getId()));
        if (result == null) {
            throw new BusinessException(ErrorCode.REPORT_DATA_INCOMPLETE,
                    "生成报告所需的分析结果不存在");
        }
        if (!task.getAudioFileId().equals(result.getAudioFileId())
                || !task.getAudioFileId().equals(audioFileId)) {
            throw new BusinessException(ErrorCode.REPORT_DATA_INCOMPLETE,
                    "分析结果与任务关联不一致");
        }

        List<AudioIssueSegment> issues = issueSegmentMapper.selectByTask(
                task.getId());
        AudioFile audioFile = audioFileMapper.selectById(audioFileId);
        GeneratedReport generated = reportGenerator.generate(audioFile,
                result, issues == null ? List.of() : issues);
        LocalDateTime now = LocalDateTime.now();
        AudioAnalysisReport report = new AudioAnalysisReport();
        report.setId(IdWorker.getId());
        report.setTaskId(task.getId());
        report.setAudioFileId(audioFileId);
        report.setReportVersion(properties.getReport().getVersion());
        report.setQualityScore(generated.qualityScore());
        report.setQualityGrade(generated.qualityGrade().name());
        report.setSummary(generated.summary());
        report.setReportJson(serializePayload(generated.payload(), task.getId()));
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        if (reportMapper.upsert(report) < 1) {
            throw new IllegalStateException("Report snapshot was not saved");
        }

        // 唯一约束并发竞争时，以数据库中的唯一行和稳定 reportId 为准。
        AudioAnalysisReport saved = reportMapper.selectByTaskId(task.getId());
        if (saved == null) {
            throw new IllegalStateException("Saved report cannot be loaded");
        }
        validateAssociation(saved, task);
        AudioAnalysisReportVO vo = toVO(saved);
        log.info("Analysis report generated, taskId={}, audioFileId={}, "
                        + "qualityScore={}, qualityGrade={}, issueCount={}, "
                        + "recommendationCount={}, reportVersion={}, elapsedMs={}",
                task.getId(), audioFileId, saved.getQualityScore(),
                saved.getQualityGrade(), vo.getIssueSummary()
                        .getTotalIssueCount(),
                vo.getRecommendations().size(), saved.getReportVersion(),
                System.currentTimeMillis() - startedAt);
        return vo;
    }

    private String serializePayload(Payload payload, Long taskId) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Unable to serialize report payload, taskId={}",
                    taskId, e);
            throw new IllegalStateException(
                    "Unable to serialize report payload", e);
        }
    }

    private AudioAnalysisReportVO toVO(AudioAnalysisReport report) {
        Payload payload;
        try {
            payload = objectMapper.readValue(report.getReportJson(),
                    Payload.class);
        } catch (Exception e) {
            log.error("Unable to parse report payload, reportId={}, taskId={}",
                    report.getId(), report.getTaskId(), e);
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED,
                    "报告数据解析失败");
        }
        if (payload == null || payload.getAudioOverview() == null
                || payload.getIssueSummary() == null) {
            log.error("Report payload is incomplete, reportId={}, taskId={}",
                    report.getId(), report.getTaskId());
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED,
                    "报告数据解析失败");
        }
        QualityGrade grade;
        try {
            grade = QualityGrade.valueOf(report.getQualityGrade());
        } catch (Exception e) {
            log.error("Invalid report grade, reportId={}, taskId={}",
                    report.getId(), report.getTaskId(), e);
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED,
                    "报告数据解析失败");
        }
        return AudioAnalysisReportVO.builder()
                .reportId(report.getId())
                .reportVersion(report.getReportVersion())
                .taskId(report.getTaskId())
                .audioFileId(report.getAudioFileId())
                .qualityScore(report.getQualityScore())
                .qualityGrade(grade.name())
                .qualityGradeText(grade.getDisplayText())
                .summary(report.getSummary())
                .audioOverview(payload.getAudioOverview())
                .loudnessOverview(payload.getLoudnessOverview())
                .issueSummary(payload.getIssueSummary())
                .keyIssues(payload.getKeyIssues() == null
                        ? List.of() : payload.getKeyIssues())
                .timeline(payload.getTimeline() == null
                        ? List.of() : payload.getTimeline())
                .recommendations(payload.getRecommendations() == null
                        ? List.of() : payload.getRecommendations())
                .generatedAt(report.getUpdatedAt() == null
                        ? report.getCreatedAt() : report.getUpdatedAt())
                .build();
    }

    private void validateAssociation(AudioAnalysisReport report,
                                     AudioAnalysisTask task) {
        if (!task.getId().equals(report.getTaskId())
                || !task.getAudioFileId().equals(report.getAudioFileId())) {
            log.error("Report association mismatch, reportId={}, taskId={}",
                    report.getId(), task.getId());
            throw new BusinessException(ErrorCode.REPORT_DATA_INCOMPLETE,
                    "报告与任务关联不一致");
        }
    }

    private void validateTaskId(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "taskId must be greater than 0");
        }
    }
}
