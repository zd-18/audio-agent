package com.audioagent.contentanalysis.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.client.DeepSeekClient;
import com.audioagent.contentanalysis.client.DeepSeekResponse;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.prompt.ContentAnalysisPromptV1;
import com.audioagent.contentanalysis.prompt.ContentAnalysisRepairPromptV1;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationException;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationStage;
import com.audioagent.contentanalysis.validation.AnalysisResultValidator;
import com.audioagent.contentanalysis.validation.EvidenceQuoteResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentAnalysisEngine {

    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties properties;
    private final ContentAnalysisPromptV1 prompt;
    private final ContentAnalysisRepairPromptV1 repairPrompt;
    private final EvidenceQuoteResolver evidenceQuoteResolver;
    private final AnalysisResultValidator validator;
    private final ObjectMapper objectMapper;

    public ContentAnalysisExecution analyze(
            Set<AnalysisType> requestedTypes,
            SummaryStyle summaryStyle,
            String language,
            Long durationMs,
            List<SourceChunk> chunks) {
        return analyze(
                requestedTypes,
                summaryStyle,
                language,
                durationMs,
                chunks,
                null);
    }

    public ContentAnalysisExecution analyze(
            Set<AnalysisType> requestedTypes,
            SummaryStyle summaryStyle,
            String language,
            Long durationMs,
            List<SourceChunk> chunks,
            ContentAnalysisDiagnosticContext diagnosticContext) {
        if (chunks == null || chunks.isEmpty()) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_INPUT_TOO_LARGE, false,
                    "文字稿没有可分析的内容片段");
        }
        Set<AnalysisType> types = requestedTypes == null
                || requestedTypes.isEmpty()
                ? EnumSet.allOf(AnalysisType.class)
                : EnumSet.copyOf(requestedTypes);
        SummaryStyle style = summaryStyle == null
                ? SummaryStyle.STANDARD : summaryStyle;
        CallBudget budget = new CallBudget(
                properties.getMaxMapCalls() + 2);
        TokenTotals totals = new TokenTotals();
        ValidatedResponse finalResponse;

        String finalUserPrompt = prompt.finalPrompt(
                types, style, language, durationMs, chunks);
        if (fits(finalUserPrompt)) {
            finalResponse = invokeValidated(
                    prompt.systemPrompt(), finalUserPrompt,
                    chunks, budget, totals, diagnosticContext);
        } else {
            finalResponse = analyzeMapReduce(
                    types, style, chunks, budget, totals,
                    diagnosticContext);
        }
        ContentAnalysisOutput filtered = filterAndSort(
                finalResponse.output(), types);
        String model = finalResponse.response().model();
        if (model == null || model.isBlank()) {
            model = properties.getModel();
        }
        return new ContentAnalysisExecution(
                filtered, model,
                totals.promptTokens,
                totals.completionTokens,
                totals.totalTokens,
                budget.used);
    }

    private ValidatedResponse analyzeMapReduce(
            Set<AnalysisType> types,
            SummaryStyle style,
            List<SourceChunk> chunks,
            CallBudget budget,
            TokenTotals totals,
            ContentAnalysisDiagnosticContext diagnosticContext) {
        List<List<SourceChunk>> batches = partition(chunks);
        if (batches.size() > properties.getMaxMapCalls()) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_INPUT_TOO_LARGE, false,
                    "文字稿过长，超过受控分块分析上限");
        }
        List<String> partialResults = new ArrayList<>();
        for (int index = 0; index < batches.size(); index++) {
            List<SourceChunk> batch = batches.get(index);
            String mapUserPrompt = prompt.mapPrompt(
                    types, style, index + 1, batches.size(), batch);
            if (!fits(mapUserPrompt)) {
                throw new ContentAnalysisException(
                        ErrorCode.AI_INPUT_TOO_LARGE, false,
                        "单个文字稿分块超过智能分析安全限制");
            }
            ValidatedResponse partial = invokeValidated(
                    prompt.systemPrompt(), mapUserPrompt,
                    batch, budget, totals, diagnosticContext);
            partialResults.add(json(partial.output()));
        }
        String reduceUserPrompt = prompt.reducePrompt(
                types, style, partialResults, chunks);
        if (!fits(reduceUserPrompt)) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_INPUT_TOO_LARGE, false,
                    "分块分析结果超过受控合并上限");
        }
        return invokeValidated(prompt.systemPrompt(), reduceUserPrompt,
                chunks, budget, totals, diagnosticContext);
    }

    private ValidatedResponse invokeValidated(
            String systemPrompt,
            String userPrompt,
            List<SourceChunk> chunks,
            CallBudget budget,
            TokenTotals totals,
            ContentAnalysisDiagnosticContext diagnosticContext) {
        DeepSeekResponse first = invoke(
                systemPrompt, userPrompt, budget, totals);
        EvidenceQuoteResolver.ResolutionSummary firstResolution = null;
        try {
            AnalysisResultValidator.ParsedResult parsed = validator.parse(
                    first.content(), chunks,
                    AnalysisResultValidationStage.INITIAL_PARSE);
            firstResolution = evidenceQuoteResolver.resolve(
                    parsed.output(), chunks);
            ContentAnalysisOutput output = validator.validateAndEnrich(
                    parsed.output(), chunks,
                    AnalysisResultValidationStage.INITIAL_VALIDATION,
                    parsed.diagnostics());
            logEvidenceResolution(
                    diagnosticContext,
                    AnalysisResultValidationStage.INITIAL_VALIDATION,
                    firstResolution, true);
            return new ValidatedResponse(
                    output,
                    first);
        } catch (AnalysisResultValidationException firstFailure) {
            if (firstResolution != null) {
                logEvidenceResolution(
                        diagnosticContext,
                        AnalysisResultValidationStage.INITIAL_VALIDATION,
                        firstResolution, false);
            }
            logValidationFailure(
                    diagnosticContext, first, firstFailure);
            String repairUserPrompt = repairPrompt.repairPrompt(
                    first.content(), firstFailure, chunks);
            if (!fits(
                    repairPrompt.systemPrompt(), repairUserPrompt)) {
                throw invalidResponse(firstFailure);
            }
            DeepSeekResponse repaired = invoke(
                    repairPrompt.systemPrompt(), repairUserPrompt,
                    budget, totals);
            EvidenceQuoteResolver.ResolutionSummary repairResolution = null;
            try {
                AnalysisResultValidator.ParsedResult parsed =
                        validator.parse(
                                repaired.content(), chunks,
                                AnalysisResultValidationStage.REPAIR_PARSE);
                repairResolution = evidenceQuoteResolver.resolve(
                        parsed.output(), chunks);
                ContentAnalysisOutput output =
                        validator.validateAndEnrich(
                                parsed.output(), chunks,
                                AnalysisResultValidationStage
                                        .REPAIR_VALIDATION,
                                parsed.diagnostics());
                logEvidenceResolution(
                        diagnosticContext,
                        AnalysisResultValidationStage.REPAIR_VALIDATION,
                        repairResolution, false);
                return new ValidatedResponse(
                        output,
                        repaired);
            } catch (AnalysisResultValidationException repairedFailure) {
                if (repairResolution != null) {
                    logEvidenceResolution(
                            diagnosticContext,
                            AnalysisResultValidationStage
                                    .REPAIR_VALIDATION,
                            repairResolution, false);
                }
                logValidationFailure(
                        diagnosticContext, repaired, repairedFailure);
                throw invalidResponse(repairedFailure);
            }
        }
    }

    private void logEvidenceResolution(
            ContentAnalysisDiagnosticContext context,
            AnalysisResultValidationStage stage,
            EvidenceQuoteResolver.ResolutionSummary summary,
            boolean repairSkipped) {
        for (EvidenceQuoteResolver.Resolution item : summary.items()) {
            log.info("Content analysis evidence quote resolved, "
                            + "taskId={}, transcriptId={}, stage={}, "
                            + "resultItemType={}, itemIndex={}, "
                            + "sourceChunkId={}, originalQuoteLength={}, "
                            + "matchMethod={}, finalQuoteLength={}, "
                            + "unresolvedReason={}, repairSkipped={}",
                    context == null ? null : context.taskId(),
                    context == null ? null : context.transcriptId(),
                    stage,
                    item.itemType(),
                    item.itemIndex(),
                    item.sourceChunkId(),
                    item.originalQuoteLength(),
                    item.matchMethod(),
                    item.finalQuoteLength(),
                    item.unresolvedReason(),
                    repairSkipped);
        }
        log.info("Content analysis evidence quote summary, "
                        + "taskId={}, transcriptId={}, stage={}, "
                        + "exactMatchCount={}, normalizedMatchCount={}, "
                        + "fallbackCount={}, unresolvedCount={}, "
                        + "repairSkipped={}",
                context == null ? null : context.taskId(),
                context == null ? null : context.transcriptId(),
                stage,
                summary.exactMatchCount(),
                summary.normalizedMatchCount(),
                summary.fallbackCount(),
                summary.unresolvedCount(),
                repairSkipped);
    }

    private void logValidationFailure(
            ContentAnalysisDiagnosticContext context,
            DeepSeekResponse response,
            AnalysisResultValidationException failure) {
        var diagnostics = failure.getResponseDiagnostics();
        String responseModel = response == null ? null : response.model();
        String modelName = responseModel == null || responseModel.isBlank()
                ? context == null ? properties.getModel()
                : context.modelName()
                : responseModel;
        String promptVersion =
                failure.getStage() == AnalysisResultValidationStage
                        .REPAIR_PARSE
                        || failure.getStage()
                        == AnalysisResultValidationStage.REPAIR_VALIDATION
                        ? ContentAnalysisRepairPromptV1.VERSION
                        : context == null || context.promptVersion() == null
                        ? ContentAnalysisPromptV1.VERSION
                        : context.promptVersion();
        log.warn("Content analysis validation failed, taskId={}, "
                        + "transcriptId={}, stage={}, diagnosticCode={}, "
                        + "modelName={}, promptVersion={}, "
                        + "sourceChunkCount={}, allowedChunkIds={}, "
                        + "summaryPresent={}, keyPointCount={}, "
                        + "chapterCount={}, speechIssueCount={}, "
                        + "errorCodes={}, invalidFields={}, "
                        + "responseLength={}, responseSha256={}, "
                        + "topLevelFieldNames={}, firstCharacterType={}, "
                        + "lastCharacterType={}, "
                        + "markdownCodeFencePresent={}",
                context == null ? null : context.taskId(),
                context == null ? null : context.transcriptId(),
                failure.getStage(),
                failure.getDiagnosticCode(),
                modelName,
                promptVersion,
                failure.getAllowedChunkIds().size(),
                failure.getAllowedChunkIds(),
                diagnostics == null
                        ? null : diagnostics.summaryPresent(),
                diagnostics == null
                        ? null : diagnostics.keyPointCount(),
                diagnostics == null
                        ? null : diagnostics.chapterCount(),
                diagnostics == null
                        ? null : diagnostics.speechIssueCount(),
                failure.getErrorCodes(),
                failure.getInvalidFields(),
                diagnostics == null
                        ? null : diagnostics.responseLength(),
                diagnostics == null
                        ? null : diagnostics.responseSha256(),
                diagnostics == null
                        ? List.of() : diagnostics.topLevelFieldNames(),
                diagnostics == null
                        ? null : diagnostics.firstCharacterType(),
                diagnostics == null
                        ? null : diagnostics.lastCharacterType(),
                diagnostics != null
                        && diagnostics.markdownCodeFencePresent());
    }

    private DeepSeekResponse invoke(String systemPrompt,
                                    String userPrompt,
                                    CallBudget budget,
                                    TokenTotals totals) {
        budget.consume();
        DeepSeekResponse response = deepSeekClient.complete(
                systemPrompt, userPrompt);
        totals.add(response);
        return response;
    }

    private List<List<SourceChunk>> partition(
            List<SourceChunk> chunks) {
        int textBudget = Math.max(1_000,
                properties.getMaxInputChars() - 8_000);
        List<List<SourceChunk>> batches = new ArrayList<>();
        List<SourceChunk> current = new ArrayList<>();
        int currentChars = 0;
        for (SourceChunk chunk : chunks) {
            int size = chunk.text() == null
                    ? 200 : chunk.text().length() + 200;
            if (!current.isEmpty()
                    && currentChars + size > textBudget) {
                batches.add(List.copyOf(current));
                current.clear();
                currentChars = 0;
            }
            current.add(chunk);
            currentChars += size;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private ContentAnalysisOutput filterAndSort(
            ContentAnalysisOutput output,
            Set<AnalysisType> types) {
        if (!types.contains(AnalysisType.SUMMARY)) {
            output.setSummary(new ContentAnalysisOutput.Summary(
                    "", "", List.of()));
        }
        if (!types.contains(AnalysisType.KEY_POINTS)) {
            output.setKeyPoints(List.of());
        } else {
            output.setKeyPoints(output.getKeyPoints().stream()
                    .sorted(Comparator.comparing(
                            ContentAnalysisOutput.KeyPoint::getOrder))
                    .toList());
        }
        if (!types.contains(AnalysisType.CHAPTERS)) {
            output.setChapters(List.of());
        } else {
            output.setChapters(output.getChapters().stream()
                    .sorted(Comparator.comparing(
                            ContentAnalysisOutput.Chapter::getOrder))
                    .toList());
        }
        if (!types.contains(AnalysisType.SPEECH_ISSUES)) {
            output.setSpeechIssues(List.of());
        } else {
            output.setSpeechIssues(output.getSpeechIssues().stream()
                    .sorted(Comparator.comparing(
                            ContentAnalysisOutput.SpeechIssue::getOrder))
                    .toList());
        }
        return output;
    }

    private boolean fits(String userPrompt) {
        return fits(prompt.systemPrompt(), userPrompt);
    }

    private boolean fits(String systemPrompt, String userPrompt) {
        return systemPrompt.length() + userPrompt.length()
                <= properties.getMaxInputChars();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ContentAnalysisException(
                    ErrorCode.AI_ANALYSIS_FAILED, false,
                    "智能分析结果整理失败", e);
        }
    }

    private ContentAnalysisException invalidResponse(
            AnalysisResultValidationException cause) {
        String message;
        if (cause.getStage() == AnalysisResultValidationStage.INITIAL_PARSE
                || cause.getStage()
                == AnalysisResultValidationStage.REPAIR_PARSE
                || cause.getErrorCodes().stream().anyMatch(code ->
                code == AnalysisResultValidationErrorCode.MISSING_SUMMARY
                        || code == AnalysisResultValidationErrorCode
                        .NULL_ARRAY)) {
            message = "智能分析返回结果不完整，请重试。";
        } else if (cause.getErrorCodes().stream().anyMatch(code ->
                code == AnalysisResultValidationErrorCode
                        .EVIDENCE_QUOTE_NOT_FOUND
                        || code == AnalysisResultValidationErrorCode
                        .EVIDENCE_QUOTE_TOO_LONG
                        || code == AnalysisResultValidationErrorCode
                        .MISSING_EVIDENCE_QUOTE
                        || code == AnalysisResultValidationErrorCode
                        .MISSING_EVIDENCE_CHUNK_IDS
                        || code == AnalysisResultValidationErrorCode
                        .UNKNOWN_CHUNK_ID)) {
            message = "智能分析中的原文引用无法验证，请重试。";
        } else {
            message = "智能分析结果格式不正确，请重试。";
        }
        return new ContentAnalysisException(
                ErrorCode.AI_RESPONSE_INVALID, false,
                message, cause);
    }

    private record ValidatedResponse(
            ContentAnalysisOutput output,
            DeepSeekResponse response) {
    }

    private static final class CallBudget {
        private final int maximum;
        private int used;

        private CallBudget(int maximum) {
            this.maximum = maximum;
        }

        private void consume() {
            if (used >= maximum) {
                throw new ContentAnalysisException(
                        ErrorCode.AI_INPUT_TOO_LARGE, false,
                        "智能分析调用次数超过受控上限");
            }
            used++;
        }
    }

    private static final class TokenTotals {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        private void add(DeepSeekResponse response) {
            promptTokens += zero(response.promptTokens());
            completionTokens += zero(response.completionTokens());
            totalTokens += zero(response.totalTokens());
        }

        private int zero(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
