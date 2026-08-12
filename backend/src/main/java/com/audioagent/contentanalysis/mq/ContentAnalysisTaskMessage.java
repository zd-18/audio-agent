package com.audioagent.contentanalysis.mq;

import java.io.Serial;
import java.io.Serializable;

public record ContentAnalysisTaskMessage(
        Long taskId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
