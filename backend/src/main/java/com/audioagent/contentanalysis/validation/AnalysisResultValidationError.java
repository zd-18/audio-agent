package com.audioagent.contentanalysis.validation;

public record AnalysisResultValidationError(
        AnalysisResultValidationErrorCode code,
        String message,
        String field) {
}
