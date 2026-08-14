package com.audioagent.progress.service.impl;

import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mapper.AudioProcessingConfirmationMapper;
import com.audioagent.analysis.mapper.AudioProcessingPlanMapper;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.mapper.AudioContentAnalysisTaskMapper;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.progress.service.UserTaskProgressAssembler;
import com.audioagent.progress.service.UserTaskProgressService;
import com.audioagent.progress.service.UserTaskProgressSnapshot;
import com.audioagent.progress.vo.UserTaskProgressVO;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.audioagent.transcription.mapper.AudioTranscriptionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserTaskProgressServiceImpl implements UserTaskProgressService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AudioAnalysisTaskMapper analysisTaskMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioTranscriptionTaskMapper transcriptionTaskMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final AudioContentAnalysisTaskMapper contentAnalysisTaskMapper;
    private final AudioProcessingPlanMapper processingPlanMapper;
    private final AudioProcessingConfirmationMapper confirmationMapper;
    private final AudioProcessingExecutionMapper executionMapper;
    private final AudioResourceOwnershipService ownershipService;
    private final UserTaskProgressAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserTaskProgressVO> list(Long userId, int current,
                                               int size) {
        validate(userId, current, size);
        IPage<TaskListVO> page = analysisTaskMapper.selectTaskPage(
                new Page<>(current, size), userId, null, null, null, null);
        List<UserTaskProgressVO> records = aggregate(userId,
                page.getRecords());
        return PageResult.of(records, page.getCurrent(), page.getSize(),
                page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public UserTaskProgressVO get(Long userId, Long taskId) {
        if (userId == null || userId <= 0 || taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        ownershipService.requireTaskOwned(userId, taskId);
        AudioAnalysisTask task = analysisTaskMapper.selectById(taskId);
        AudioFile file = audioFileMapper.selectById(task.getAudioFileId());
        TaskListVO view = new TaskListVO();
        view.setTaskId(task.getId());
        view.setAudioFileId(task.getAudioFileId());
        view.setFileName(file == null ? null : file.getOriginalName());
        view.setAnalysisType(task.getAnalysisType() == null
                ? null : task.getAnalysisType().name());
        view.setStatus(task.getStatus() == null
                ? null : task.getStatus().name());
        view.setProgress(task.getProgress());
        view.setLastErrorCode(task.getLastErrorCode());
        view.setErrorMessage(task.getErrorMessage());
        view.setCreatedAt(task.getCreatedAt());
        view.setStartedAt(task.getStartedAt());
        view.setFinishedAt(task.getFinishedAt());
        List<UserTaskProgressVO> result = aggregate(userId, List.of(view));
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.AUDIO_TASK_NOT_FOUND);
        }
        return result.getFirst();
    }

    private List<UserTaskProgressVO> aggregate(Long userId,
                                               List<TaskListVO> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream().map(TaskListVO::getTaskId)
                .distinct().toList();
        List<Long> fileIds = tasks.stream().map(TaskListVO::getAudioFileId)
                .distinct().toList();

        Map<Long, AudioFile> files = audioFileMapper.selectBatchIds(fileIds)
                .stream().collect(Collectors.toMap(AudioFile::getId,
                        Function.identity()));
        Map<Long, AudioTranscriptionTask> transcriptions =
                transcriptionTaskMapper.selectLatestForAudioFiles(userId,
                                fileIds).stream()
                        .collect(Collectors.toMap(
                                AudioTranscriptionTask::getAudioFileId,
                                Function.identity()));

        List<Long> transcriptionIds = transcriptions.values().stream()
                .map(AudioTranscriptionTask::getId).toList();
        Map<Long, AudioTranscript> transcriptsByTask = transcriptionIds
                .isEmpty() ? Collections.emptyMap()
                : transcriptMapper.selectList(
                        new LambdaQueryWrapper<AudioTranscript>()
                                .eq(AudioTranscript::getUserId, userId)
                                .in(AudioTranscript::getTranscriptionTaskId,
                                        transcriptionIds))
                        .stream().collect(Collectors.toMap(
                                AudioTranscript::getTranscriptionTaskId,
                                Function.identity(), (left, right) -> left));

        List<Long> transcriptIds = transcriptsByTask.values().stream()
                .map(AudioTranscript::getId).toList();
        Map<Long, AudioContentAnalysisTask> contentByTranscript = transcriptIds
                .isEmpty() ? Collections.emptyMap()
                : contentAnalysisTaskMapper.selectLatestForTranscripts(
                                userId, transcriptIds).stream()
                        .collect(Collectors.toMap(
                                AudioContentAnalysisTask::getTranscriptId,
                                Function.identity()));

        Map<Long, AudioProcessingPlan> plans = processingPlanMapper.selectList(
                        new LambdaQueryWrapper<AudioProcessingPlan>()
                                .in(AudioProcessingPlan::getTaskId, taskIds))
                .stream().collect(Collectors.toMap(
                        AudioProcessingPlan::getTaskId, Function.identity()));
        List<Long> planIds = plans.values().stream()
                .map(AudioProcessingPlan::getId).toList();
        Map<Long, AudioProcessingConfirmation> confirmationsByPlan = planIds
                .isEmpty() ? Collections.emptyMap()
                : confirmationMapper.selectList(
                        new LambdaQueryWrapper<AudioProcessingConfirmation>()
                                .in(AudioProcessingConfirmation::getPlanId,
                                        planIds)
                                .orderByDesc(AudioProcessingConfirmation::getCreatedAt))
                        .stream().collect(Collectors.toMap(
                                AudioProcessingConfirmation::getPlanId,
                                Function.identity(), (latest, ignored) -> latest,
                                LinkedHashMap::new));
        List<Long> confirmationIds = confirmationsByPlan.values().stream()
                .map(AudioProcessingConfirmation::getId).toList();
        Map<Long, AudioProcessingExecution> executionsByConfirmation =
                confirmationIds.isEmpty() ? Collections.emptyMap()
                        : executionMapper.selectList(
                        new LambdaQueryWrapper<AudioProcessingExecution>()
                                .in(AudioProcessingExecution::getConfirmationId,
                                        confirmationIds))
                        .stream().collect(Collectors.toMap(
                                AudioProcessingExecution::getConfirmationId,
                                Function.identity()));

        return tasks.stream().map(task -> {
            AudioTranscriptionTask transcription =
                    transcriptions.get(task.getAudioFileId());
            AudioTranscript transcript = transcription == null ? null
                    : transcriptsByTask.get(transcription.getId());
            AudioContentAnalysisTask content = transcript == null ? null
                    : contentByTranscript.get(transcript.getId());
            AudioProcessingPlan plan = plans.get(task.getTaskId());
            AudioProcessingConfirmation confirmation = plan == null ? null
                    : confirmationsByPlan.get(plan.getId());
            if (confirmation != null && plan.getPlanRevision() != null
                    && !plan.getPlanRevision().equals(
                    confirmation.getSourcePlanRevision())) {
                confirmation = null;
            }
            AudioProcessingExecution execution = confirmation == null ? null
                    : executionsByConfirmation.get(confirmation.getId());
            return assembler.assemble(new UserTaskProgressSnapshot(
                    task, files.get(task.getAudioFileId()), transcription,
                    transcript, content, plan, confirmation, execution));
        }).toList();
    }

    private void validate(Long userId, int current, int size) {
        if (userId == null || userId <= 0 || current < 1
                || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }
}
