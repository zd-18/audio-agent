package com.audioagent.contentanalysis.executor;

import com.audioagent.contentanalysis.model.ContentAnalysisOutput;

public record ContentAnalysisExecution(
        ContentAnalysisOutput output,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int callCount) {
}
