package com.audioagent.progress.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserTaskProgressVO {

    private String taskId;
    private String audioFileId;
    private String fileName;
    private String status;
    private String statusLabel;
    private String currentStage;
    private String currentStageLabel;
    private Integer progressPercent;
    private String currentActivity;
    private Boolean requiresUserAction;
    private String failureReason;
    private List<Stage> stages;
    private List<Action> nextActions;
    private List<String> completedOperations;
    private String resultPath;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @Data
    @Builder
    public static class Stage {
        private String code;
        private String label;
        private String status;
        private Integer progressPercent;
        private String description;
    }

    @Data
    @Builder
    public static class Action {
        private String type;
        private String label;
        private String path;
        private Boolean primary;
    }
}
