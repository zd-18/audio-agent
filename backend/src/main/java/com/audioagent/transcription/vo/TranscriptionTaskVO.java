package com.audioagent.transcription.vo;

import com.audioagent.transcription.entity.AudioTranscriptionTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionTaskVO {
    private String taskId;
    private String audioFileId;
    private String audioFileName;
    private String status;
    private String language;
    private Boolean enableSpeakerDiarization;
    private Integer progressPercent;
    private Integer retryCount;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TranscriptionTaskVO from(AudioTranscriptionTask task,
                                           String audioFileName) {
        return TranscriptionTaskVO.builder()
                .taskId(task.getId().toString())
                .audioFileId(task.getAudioFileId().toString())
                .audioFileName(audioFileName)
                .status(task.getStatus().name())
                .language(task.getLanguage())
                .enableSpeakerDiarization(
                        task.getEnableSpeakerDiarization())
                .progressPercent(task.getProgressPercent())
                .retryCount(task.getRetryCount())
                .failureCode(task.getFailureCode())
                .failureMessage(task.getFailureMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
