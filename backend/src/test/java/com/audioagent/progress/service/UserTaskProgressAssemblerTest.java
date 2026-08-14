package com.audioagent.progress.service;

import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.common.enums.FileStatus;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.progress.vo.UserTaskProgressVO;
import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.model.TranscriptionTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTaskProgressAssemblerTest {

    private final UserTaskProgressAssembler assembler =
            new UserTaskProgressAssembler();

    @Test
    void mapsParsingFailureToProductMessageAndRecoveryAction() {
        TaskListVO task = task("FAILED");
        task.setLastErrorCode("FFPROBE_RESULT_PARSE_ERROR");
        task.setErrorMessage("ffprobe returned invalid json");

        UserTaskProgressVO result = assembler.assemble(snapshot(task,
                null, null, null));

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getCurrentStage()).isEqualTo("AUDIO_PARSING");
        assertThat(result.getFailureReason()).isEqualTo(
                "文件解析失败，请确认音频文件是否完整。");
        assertThat(result.getNextActions()).extracting(
                UserTaskProgressVO.Action::getLabel)
                .contains("重新分析");
        assertThat(result.toString()).doesNotContain("ffprobe")
                .doesNotContain("FFPROBE_RESULT_PARSE_ERROR");
    }

    @Test
    void asksUserToConfirmGeneratedPlan() {
        TaskListVO task = task("SUCCESS");
        AudioProcessingPlan plan = new AudioProcessingPlan();
        plan.setId(31L);
        plan.setTaskId(task.getTaskId());
        plan.setPlanRevision(1);
        plan.setPlanStatus("READY");

        UserTaskProgressVO result = assembler.assemble(snapshot(task,
                plan, null, null));

        assertThat(result.getStatus()).isEqualTo("WAITING_FOR_USER");
        assertThat(result.getCurrentStage()).isEqualTo("USER_CONFIRMATION");
        assertThat(result.getCurrentActivity()).contains("确认");
        assertThat(result.getNextActions()).extracting(
                UserTaskProgressVO.Action::getLabel)
                .contains("查看并确认方案");
    }

    @Test
    void exposesCompletedRecordAndResultEntryWithoutExecutionId() {
        TaskListVO task = task("SUCCESS");
        AudioProcessingPlan plan = new AudioProcessingPlan();
        plan.setId(31L);
        plan.setTaskId(task.getTaskId());
        plan.setPlanRevision(1);
        plan.setPlanStatus("READY");
        AudioProcessingConfirmation confirmation =
                new AudioProcessingConfirmation();
        confirmation.setId(41L);
        confirmation.setPlanId(plan.getId());
        confirmation.setSourcePlanRevision(1);
        confirmation.setConfirmationStatus("CONFIRMED");
        AudioProcessingExecution execution = new AudioProcessingExecution();
        execution.setId(987654321012345678L);
        execution.setExecutionStatus("SUCCESS");
        execution.setCurrentStage("COMPLETED");
        execution.setProgressPercent(100);
        execution.setFinishedAt(LocalDateTime.now());

        UserTaskProgressVO result = assembler.assemble(snapshot(task,
                plan, confirmation, execution));

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getProgressPercent()).isEqualTo(100);
        assertThat(result.getRequiresUserAction()).isFalse();
        assertThat(result.getCompletedOperations()).contains(
                "文件上传", "音频解析", "智能分析", "处理方案",
                "用户确认", "音频处理", "结果生成");
        assertThat(result.getResultPath()).isEqualTo(
                "/analysis/tasks/9007199254740993/processing-execution");
        assertThat(result.toString()).doesNotContain(
                execution.getId().toString());
    }

    private UserTaskProgressSnapshot snapshot(
            TaskListVO task,
            AudioProcessingPlan plan,
            AudioProcessingConfirmation confirmation,
            AudioProcessingExecution execution) {
        AudioFile file = new AudioFile();
        file.setId(task.getAudioFileId());
        file.setOriginalName("访谈录音.wav");
        file.setFileStatus(FileStatus.AVAILABLE);
        AudioTranscriptionTask transcription = null;
        if (execution != null) {
            transcription = new AudioTranscriptionTask();
            transcription.setId(21L);
            transcription.setAudioFileId(file.getId());
            transcription.setStatus(TranscriptionTaskStatus.SUCCESS);
            transcription.setProgressPercent(100);
        }
        return new UserTaskProgressSnapshot(task, file, transcription,
                null, null, plan, confirmation, execution);
    }

    private TaskListVO task(String status) {
        TaskListVO task = new TaskListVO();
        task.setTaskId(9007199254740993L);
        task.setAudioFileId(9007199254740995L);
        task.setFileName("访谈录音.wav");
        task.setStatus(status);
        task.setProgress("SUCCESS".equals(status) ? 100 : 0);
        task.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return task;
    }
}
