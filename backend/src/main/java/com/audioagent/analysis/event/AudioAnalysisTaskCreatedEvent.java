package com.audioagent.analysis.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AudioAnalysisTaskCreatedEvent extends ApplicationEvent {

    private final Long taskId;
    private final Long audioFileId;

    public AudioAnalysisTaskCreatedEvent(Object source, Long taskId, Long audioFileId) {
        super(source);
        this.taskId = taskId;
        this.audioFileId = audioFileId;
    }
}
