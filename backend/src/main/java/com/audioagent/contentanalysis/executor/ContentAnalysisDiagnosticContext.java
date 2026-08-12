package com.audioagent.contentanalysis.executor;

public record ContentAnalysisDiagnosticContext(
        Long taskId,
        Long transcriptId,
        String modelName,
        String promptVersion) {
}
