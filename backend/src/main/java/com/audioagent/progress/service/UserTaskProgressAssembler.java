package com.audioagent.progress.service;

import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.processing.ProcessingConfirmationStatus;
import com.audioagent.analysis.processing.ProcessingPlanStatus;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.contentanalysis.model.ContentAnalysisTaskStatus;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.model.ProcessingExecutionStage;
import com.audioagent.processing.model.ProcessingExecutionStatus;
import com.audioagent.progress.vo.UserTaskProgressVO;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class UserTaskProgressAssembler {

    private static final Set<String> PARSING_FAILURE_CODES = Set.of(
            "INVALID_AUDIO_FILE", "NO_AUDIO_STREAM", "FFPROBE_TIMEOUT",
            "FFPROBE_NOT_FOUND", "FFPROBE_RESULT_PARSE_ERROR");
    private static final Set<String> RESULT_STAGES = Set.of(
            ProcessingExecutionStage.UPLOADING.name(),
            ProcessingExecutionStage.METADATA_EXTRACTING.name(),
            ProcessingExecutionStage.COMPLETED.name());

    public UserTaskProgressVO assemble(UserTaskProgressSnapshot snapshot) {
        Map<StageCode, StageState> stages = createStages();
        List<UserTaskProgressVO.Action> actions = new ArrayList<>();

        applyUpload(snapshot, stages, actions);
        applyAnalysis(snapshot, stages, actions);
        applyTranscription(snapshot, stages, actions);
        applyContentAnalysis(snapshot, stages, actions);
        applyPlan(snapshot, stages, actions);
        applyConfirmation(snapshot, stages, actions);
        applyExecution(snapshot, stages, actions);

        StageState current = selectCurrent(stages);
        OverallStatus status = overallStatus(stages);
        int progress = (int) Math.round(stages.values().stream()
                .mapToInt(StageState::progress).average().orElse(0));
        List<String> completed = stages.values().stream()
                .filter(stage -> stage.status == StageStatus.COMPLETED)
                .map(stage -> stage.label)
                .toList();
        String taskId = snapshot.task().getTaskId().toString();

        return UserTaskProgressVO.builder()
                .taskId(taskId)
                .audioFileId(snapshot.task().getAudioFileId().toString())
                .fileName(resolveFileName(snapshot))
                .status(status.name())
                .statusLabel(status.label)
                .currentStage(current.code.name())
                .currentStageLabel(current.label)
                .progressPercent(status == OverallStatus.COMPLETED
                        ? 100 : Math.min(progress, 99))
                .currentActivity(current.description)
                .requiresUserAction(stages.values().stream().anyMatch(stage ->
                        stage.status == StageStatus.ACTION_REQUIRED
                                || stage.status == StageStatus.FAILED))
                .failureReason(firstFailure(stages))
                .stages(stages.values().stream().map(StageState::toVO).toList())
                .nextActions(actions.stream().distinct().toList())
                .completedOperations(completed)
                .resultPath(status == OverallStatus.COMPLETED
                        ? executionPath(taskId) : null)
                .createdAt(snapshot.task().getCreatedAt())
                .completedAt(snapshot.execution() == null
                        ? null : snapshot.execution().getFinishedAt())
                .build();
    }

    private void applyUpload(UserTaskProgressSnapshot snapshot,
                             Map<StageCode, StageState> stages,
                             List<UserTaskProgressVO.Action> actions) {
        StageState stage = stages.get(StageCode.FILE_UPLOAD);
        AudioFile file = snapshot.audioFile();
        if (file == null || file.getFileStatus() == null) {
            stage.fail("文件不可用，请重新上传音频。");
            actions.add(action("UPLOAD_AGAIN", "重新上传", "/audio/upload", true));
            return;
        }
        switch (file.getFileStatus()) {
            case UPLOADING -> stage.progress("正在上传文件", 45);
            case FAILED, DELETED -> {
                stage.fail("文件上传失败，请重新上传。");
                actions.add(action("UPLOAD_AGAIN", "重新上传", "/audio/upload", true));
            }
            default -> stage.complete("文件上传完成");
        }
    }

    private void applyAnalysis(UserTaskProgressSnapshot snapshot,
                               Map<StageCode, StageState> stages,
                               List<UserTaskProgressVO.Action> actions) {
        StageState parsing = stages.get(StageCode.AUDIO_PARSING);
        StageState analysis = stages.get(StageCode.INTELLIGENT_ANALYSIS);
        AnalysisTaskStatus status = AnalysisTaskStatus.valueOf(
                snapshot.task().getStatus());
        switch (status) {
            case PENDING -> parsing.progress("正在等待解析音频", 10);
            case PROCESSING -> parsing.progress("正在解析音频内容", clamp(
                    snapshot.task().getProgress(), 15, 80));
            case SUCCESS -> {
                parsing.complete("音频解析完成");
                analysis.complete("音频智能分析完成");
            }
            case FAILED -> {
                String code = normalize(snapshot.task().getLastErrorCode());
                if (PARSING_FAILURE_CODES.contains(code)) {
                    parsing.fail("文件解析失败，请确认音频文件是否完整。");
                } else {
                    parsing.complete("音频解析完成");
                    analysis.fail("智能分析失败，请重新分析。");
                }
                actions.add(action("RETRY_ANALYSIS", "重新分析",
                        taskPath(snapshot), true));
            }
        }
    }

    private void applyTranscription(UserTaskProgressSnapshot snapshot,
                                    Map<StageCode, StageState> stages,
                                    List<UserTaskProgressVO.Action> actions) {
        StageState stage = stages.get(StageCode.SPEECH_TRANSCRIPTION);
        AudioTranscriptionTask task = snapshot.transcriptionTask();
        if (task == null) {
            stage.pending("尚未生成文字稿");
            actions.add(action("START_TRANSCRIPTION", "生成文字稿",
                    filePath(snapshot), false));
            return;
        }
        TranscriptionTaskStatus status = task.getStatus();
        if (status == null) {
            stage.pending("等待语音转写");
        } else {
            switch (status) {
                case PENDING -> stage.progress("正在等待语音转写", 10);
                case RUNNING -> stage.progress("正在将语音转换为文字",
                        clamp(task.getProgressPercent(), 5, 95));
                case SUCCESS -> stage.complete("语音转写完成");
                case FAILED -> {
                    stage.fail("语音转写失败，请重新转写。");
                    actions.add(action("RETRY_TRANSCRIPTION", "重新转写",
                            transcriptionPath(task), true));
                }
            }
        }
    }

    private void applyContentAnalysis(UserTaskProgressSnapshot snapshot,
                                      Map<StageCode, StageState> stages,
                                      List<UserTaskProgressVO.Action> actions) {
        AudioContentAnalysisTask task = snapshot.contentAnalysisTask();
        if (task == null) {
            return;
        }
        StageState stage = stages.get(StageCode.INTELLIGENT_ANALYSIS);
        ContentAnalysisTaskStatus status = task.getStatus();
        if (status == null) {
            return;
        }
        switch (status) {
            case PENDING -> stage.progress("正在等待智能分析", 10);
            case RUNNING -> stage.progress("正在理解音频内容",
                    clamp(task.getProgressPercent(), 5, 95));
            case SUCCESS -> stage.complete("智能分析完成");
            case FAILED -> {
                stage.fail("智能分析失败，请重新分析。");
                if (snapshot.transcriptionTask() != null) {
                    actions.add(action("RETRY_INTELLIGENT_ANALYSIS",
                            "重新智能分析",
                            transcriptionPath(snapshot.transcriptionTask()),
                            true));
                }
            }
        }
    }

    private void applyPlan(UserTaskProgressSnapshot snapshot,
                           Map<StageCode, StageState> stages,
                           List<UserTaskProgressVO.Action> actions) {
        StageState stage = stages.get(StageCode.PROCESSING_PLAN);
        AudioProcessingPlan plan = snapshot.processingPlan();
        if (plan == null) {
            if ("SUCCESS".equals(snapshot.task().getStatus())) {
                stage.actionRequired("分析已完成，可以生成处理方案");
                actions.add(action("GENERATE_PLAN", "生成处理方案",
                        planPath(snapshot), true));
            }
            return;
        }
        String status = normalize(plan.getPlanStatus());
        if (ProcessingPlanStatus.INVALID.name().equals(status)) {
            stage.fail("处理方案生成失败，请重新生成。");
            actions.add(action("REGENERATE_PLAN", "重新生成方案",
                    planPath(snapshot), true));
        } else {
            stage.complete("处理方案已生成");
        }
    }

    private void applyConfirmation(UserTaskProgressSnapshot snapshot,
                                   Map<StageCode, StageState> stages,
                                   List<UserTaskProgressVO.Action> actions) {
        StageState stage = stages.get(StageCode.USER_CONFIRMATION);
        if (snapshot.processingPlan() == null) {
            return;
        }
        AudioProcessingConfirmation confirmation = snapshot.confirmation();
        if (confirmation == null) {
            stage.actionRequired("处理方案等待你的确认");
            actions.add(action("REVIEW_PLAN", "查看并确认方案",
                    planPath(snapshot), true));
            return;
        }
        String status = normalize(confirmation.getConfirmationStatus());
        if (ProcessingConfirmationStatus.CONFIRMED.name().equals(status)) {
            stage.complete("处理方案已确认");
        } else if (ProcessingConfirmationStatus.DRAFT.name().equals(status)) {
            stage.actionRequired("请确认希望执行的处理步骤");
            actions.add(action("CONFIRM_PLAN", "继续确认",
                    planPath(snapshot), true));
        } else {
            stage.actionRequired("当前方案需要重新确认");
            actions.add(action("REVIEW_PLAN", "重新确认方案",
                    planPath(snapshot), true));
        }
    }

    private void applyExecution(UserTaskProgressSnapshot snapshot,
                                Map<StageCode, StageState> stages,
                                List<UserTaskProgressVO.Action> actions) {
        StageState processing = stages.get(StageCode.AUDIO_PROCESSING);
        StageState result = stages.get(StageCode.RESULT_GENERATION);
        AudioProcessingConfirmation confirmation = snapshot.confirmation();
        AudioProcessingExecution execution = snapshot.execution();
        if (execution == null) {
            if (confirmation != null && ProcessingConfirmationStatus.CONFIRMED
                    .name().equals(normalize(confirmation.getConfirmationStatus()))) {
                processing.actionRequired("方案已确认，可以开始处理音频");
                actions.add(action("START_PROCESSING", "开始处理",
                        executionPath(snapshot.task().getTaskId().toString()),
                        true));
            }
            return;
        }
        String status = normalize(execution.getExecutionStatus());
        int progress = clamp(execution.getProgressPercent(), 0, 100);
        boolean generatingResult = RESULT_STAGES.contains(
                normalize(execution.getCurrentStage()));

        if (ProcessingExecutionStatus.SUCCESS.name().equals(status)) {
            processing.complete("音频处理完成");
            result.complete("结果已生成，可以试听或下载");
            actions.clear();
            actions.add(action("VIEW_RESULT", "查看处理结果",
                    executionPath(snapshot.task().getTaskId().toString()),
                    true));
        } else if (ProcessingExecutionStatus.FAILED.name().equals(status)
                || ProcessingExecutionStatus.DEAD_LETTER.name().equals(status)) {
            if (generatingResult) {
                processing.complete("音频处理完成");
                result.fail("结果生成失败，请重新处理。");
            } else {
                processing.fail("音频处理失败，请重新处理。");
            }
            actions.add(action("RETRY_PROCESSING", "重新处理",
                    executionPath(snapshot.task().getTaskId().toString()),
                    true));
        } else if (ProcessingExecutionStatus.CANCELLED.name().equals(status)) {
            processing.actionRequired("音频处理已取消，请重新确认方案");
            actions.add(action("REVIEW_PLAN", "返回处理方案",
                    planPath(snapshot), true));
        } else if (generatingResult) {
            processing.complete("音频处理完成");
            result.progress("正在生成处理结果", Math.max(10, progress));
        } else {
            processing.progress("正在处理音频", Math.max(5, progress));
        }
    }

    private Map<StageCode, StageState> createStages() {
        Map<StageCode, StageState> result = new EnumMap<>(StageCode.class);
        Arrays.stream(StageCode.values())
                .forEach(code -> result.put(code, new StageState(code)));
        return result;
    }

    private StageState selectCurrent(Map<StageCode, StageState> stages) {
        for (StageStatus status : List.of(StageStatus.FAILED,
                StageStatus.ACTION_REQUIRED, StageStatus.IN_PROGRESS)) {
            StageCode[] values = StageCode.values();
            for (int i = values.length - 1; i >= 0; i--) {
                StageState stage = stages.get(values[i]);
                if (stage.status == status) {
                    return stage;
                }
            }
        }
        return stages.values().stream()
                .filter(stage -> stage.status != StageStatus.COMPLETED)
                .findFirst()
                .orElse(stages.get(StageCode.RESULT_GENERATION));
    }

    private OverallStatus overallStatus(Map<StageCode, StageState> stages) {
        if (stages.get(StageCode.RESULT_GENERATION).status
                == StageStatus.COMPLETED) {
            return OverallStatus.COMPLETED;
        }
        if (stages.values().stream().anyMatch(stage ->
                stage.status == StageStatus.FAILED)) {
            return OverallStatus.FAILED;
        }
        if (stages.values().stream().anyMatch(stage ->
                stage.status == StageStatus.ACTION_REQUIRED)) {
            return OverallStatus.WAITING_FOR_USER;
        }
        return OverallStatus.PROCESSING;
    }

    private String firstFailure(Map<StageCode, StageState> stages) {
        return stages.values().stream()
                .filter(stage -> stage.status == StageStatus.FAILED)
                .map(StageState::description)
                .findFirst()
                .orElse(null);
    }

    private String resolveFileName(UserTaskProgressSnapshot snapshot) {
        if (snapshot.audioFile() != null
                && StringUtils.hasText(snapshot.audioFile().getOriginalName())) {
            return snapshot.audioFile().getOriginalName();
        }
        return StringUtils.hasText(snapshot.task().getFileName())
                ? snapshot.task().getFileName() : "未命名音频";
    }

    private UserTaskProgressVO.Action action(String type, String label,
                                             String path, boolean primary) {
        return UserTaskProgressVO.Action.builder()
                .type(type).label(label).path(path).primary(primary).build();
    }

    private String taskPath(UserTaskProgressSnapshot snapshot) {
        return "/analysis/tasks/" + snapshot.task().getTaskId();
    }

    private String filePath(UserTaskProgressSnapshot snapshot) {
        return "/audio/files/" + snapshot.task().getAudioFileId();
    }

    private String planPath(UserTaskProgressSnapshot snapshot) {
        return taskPath(snapshot) + "/processing-plan";
    }

    private String executionPath(String taskId) {
        return "/analysis/tasks/" + taskId + "/processing-execution";
    }

    private String transcriptionPath(AudioTranscriptionTask task) {
        return "/transcriptions/" + task.getId();
    }

    private int clamp(Integer value, int minimum, int maximum) {
        int actual = value == null ? minimum : value;
        return Math.max(minimum, Math.min(maximum, actual));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private enum OverallStatus {
        PROCESSING("处理中"),
        WAITING_FOR_USER("等待你的操作"),
        COMPLETED("已完成"),
        FAILED("未完成");

        private final String label;

        OverallStatus(String label) {
            this.label = label;
        }
    }

    private enum StageStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, ACTION_REQUIRED
    }

    private enum StageCode {
        FILE_UPLOAD("文件上传"),
        AUDIO_PARSING("音频解析"),
        SPEECH_TRANSCRIPTION("语音转写"),
        INTELLIGENT_ANALYSIS("智能分析"),
        PROCESSING_PLAN("处理方案"),
        USER_CONFIRMATION("用户确认"),
        AUDIO_PROCESSING("音频处理"),
        RESULT_GENERATION("结果生成");

        private final String label;

        StageCode(String label) {
            this.label = label;
        }
    }

    private static class StageState {
        private final StageCode code;
        private final String label;
        private StageStatus status = StageStatus.PENDING;
        private int progress;
        private String description = "尚未开始";

        private StageState(StageCode code) {
            this.code = code;
            this.label = code.label;
        }

        private void pending(String text) {
            status = StageStatus.PENDING;
            progress = 0;
            description = text;
        }

        private void progress(String text, int value) {
            status = StageStatus.IN_PROGRESS;
            progress = Math.max(0, Math.min(99, value));
            description = text;
        }

        private void complete(String text) {
            status = StageStatus.COMPLETED;
            progress = 100;
            description = text;
        }

        private void fail(String text) {
            status = StageStatus.FAILED;
            progress = 0;
            description = text;
        }

        private void actionRequired(String text) {
            status = StageStatus.ACTION_REQUIRED;
            progress = 0;
            description = text;
        }

        private int progress() {
            return progress;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }

        private UserTaskProgressVO.Stage toVO() {
            return UserTaskProgressVO.Stage.builder()
                    .code(code.name())
                    .label(label)
                    .status(status.name())
                    .progressPercent(progress)
                    .description(description)
                    .build();
        }
    }
}
