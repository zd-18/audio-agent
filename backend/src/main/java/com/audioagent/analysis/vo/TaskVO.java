package com.audioagent.analysis.vo;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long audioFileId;

    private String analysisType;

    private String status;

    private Integer progress;

    private String errorMessage;

    private Integer retryCount;

    private Integer maxRetryCount;

    private LocalDateTime nextRetryAt;

    private String lastErrorCode;

    private String lastMessageId;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private AudioAnalysisResultVO result;

    public static TaskVO from(AudioAnalysisTask task) {
        return TaskVO.builder()
                .taskId(task.getId())
                .audioFileId(task.getAudioFileId())
                .analysisType(
                        task.getAnalysisType() == null
                                ? null
                                : task.getAnalysisType().name()
                )
                .status(
                        task.getStatus() == null
                                ? null
                                : task.getStatus().name()
                )
                .progress(task.getProgress())
                .errorMessage(task.getErrorMessage())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .nextRetryAt(task.getNextRetryAt())
                .lastErrorCode(task.getLastErrorCode())
                .lastMessageId(task.getLastMessageId())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }
}
