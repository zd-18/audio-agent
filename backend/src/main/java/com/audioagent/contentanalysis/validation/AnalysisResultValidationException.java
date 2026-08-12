package com.audioagent.contentanalysis.validation;

import lombok.Getter;

import java.util.List;

@Getter
public class AnalysisResultValidationException extends RuntimeException {

    private final AnalysisResultValidationStage stage;
    private final List<AnalysisResultValidationError> validationErrors;
    private final List<AnalysisResultValidationErrorCode> errorCodes;
    private final List<String> errorMessages;
    private final List<String> invalidFields;
    private final List<String> allowedChunkIds;
    private final AnalysisResponseDiagnostics responseDiagnostics;

    public AnalysisResultValidationException(
            AnalysisResultValidationStage stage,
            List<AnalysisResultValidationError> validationErrors,
            List<String> allowedChunkIds,
            AnalysisResponseDiagnostics responseDiagnostics,
            Throwable cause) {
        super("Content analysis result validation failed", cause);
        this.stage = stage;
        this.validationErrors = List.copyOf(validationErrors);
        this.errorCodes = validationErrors.stream()
                .map(AnalysisResultValidationError::code)
                .distinct()
                .toList();
        this.errorMessages = validationErrors.stream()
                .map(AnalysisResultValidationError::message)
                .distinct()
                .toList();
        this.invalidFields = validationErrors.stream()
                .map(AnalysisResultValidationError::field)
                .filter(field -> field != null && !field.isBlank())
                .distinct()
                .toList();
        this.allowedChunkIds = allowedChunkIds == null
                ? List.of() : List.copyOf(allowedChunkIds);
        this.responseDiagnostics = responseDiagnostics;
    }

    public AnalysisResultValidationErrorCode getDiagnosticCode() {
        return switch (stage) {
            case INITIAL_PARSE ->
                    AnalysisResultValidationErrorCode.JSON_PARSE_FAILED;
            case INITIAL_VALIDATION ->
                    AnalysisResultValidationErrorCode.RESULT_VALIDATION_FAILED;
            case REPAIR_PARSE ->
                    AnalysisResultValidationErrorCode.REPAIR_JSON_PARSE_FAILED;
            case REPAIR_VALIDATION ->
                    AnalysisResultValidationErrorCode
                            .REPAIR_RESULT_VALIDATION_FAILED;
        };
    }

    /**
     * Kept for callers that previously consumed the unstructured messages.
     */
    public List<String> getErrors() {
        return errorMessages;
    }
}
