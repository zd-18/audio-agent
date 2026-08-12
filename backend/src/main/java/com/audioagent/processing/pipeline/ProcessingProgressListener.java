package com.audioagent.processing.pipeline;

import com.audioagent.processing.model.ProcessingExecutionStage;

@FunctionalInterface
public interface ProcessingProgressListener {
    void onStage(ProcessingExecutionStage stage, int progressPercent);
}
