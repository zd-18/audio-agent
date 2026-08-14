package com.audioagent.progress.service;

import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.entity.AudioTranscriptionTask;

public record UserTaskProgressSnapshot(
        TaskListVO task,
        AudioFile audioFile,
        AudioTranscriptionTask transcriptionTask,
        AudioTranscript transcript,
        AudioContentAnalysisTask contentAnalysisTask,
        AudioProcessingPlan processingPlan,
        AudioProcessingConfirmation confirmation,
        AudioProcessingExecution execution
) {
}
