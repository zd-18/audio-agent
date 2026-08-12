package com.audioagent.contentanalysis.validation;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.CHAPTER_ORDER_REVERSED;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.CHAPTER_RANGE_REVERSED;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.DUPLICATE_ORDER;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.EVIDENCE_QUOTE_NOT_FOUND;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.EVIDENCE_QUOTE_TOO_LONG;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.INVALID_ISSUE_TYPE;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.INVALID_ORDER;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.INVALID_SEVERITY;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.MISSING_EVIDENCE_CHUNK_IDS;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.MISSING_EVIDENCE_QUOTE;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.MISSING_SUMMARY;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.NULL_ARRAY;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.NULL_ARRAY_ITEM;
import static com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode.UNKNOWN_CHUNK_ID;

@Component
@RequiredArgsConstructor
public class AnalysisResultValidator {

    private final ObjectMapper objectMapper;
    private final DeepSeekProperties properties;

    public ContentAnalysisOutput parseValidateAndEnrich(
            String json, List<SourceChunk> chunks) {
        return parseValidateAndEnrich(
                json, chunks,
                AnalysisResultValidationStage.INITIAL_PARSE,
                AnalysisResultValidationStage.INITIAL_VALIDATION);
    }

    public ContentAnalysisOutput parseValidateAndEnrich(
            String json,
            List<SourceChunk> chunks,
            AnalysisResultValidationStage parseStage,
            AnalysisResultValidationStage validationStage) {
        ParsedResult parsed = parse(json, chunks, parseStage);
        return validateAndEnrich(
                parsed.output(), chunks, validationStage,
                parsed.diagnostics());
    }

    public ParsedResult parse(
            String json,
            List<SourceChunk> chunks,
            AnalysisResultValidationStage parseStage) {
        List<SourceChunk> safeChunks = chunks == null
                ? List.of() : List.copyOf(chunks);
        List<String> allowedChunkIds = safeChunks.stream()
                .map(SourceChunk::chunkId)
                .toList();
        if (json == null || json.isBlank()
                || json.length() > properties.getMaxOutputChars()) {
            throw parseFailure(
                    parseStage,
                    json,
                    allowedChunkIds,
                    "JSON 输出为空或超过安全大小限制",
                    null);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException | RuntimeException e) {
            throw parseFailure(
                    parseStage,
                    json,
                    allowedChunkIds,
                    "JSON 无法解析为约定结构",
                    e);
        }
        AnalysisResponseDiagnostics diagnostics =
                AnalysisResponseDiagnostics.from(json, root);
        if (root == null || !root.isObject()) {
            throw parseFailure(
                    parseStage,
                    json,
                    allowedChunkIds,
                    "JSON 根节点必须是对象",
                    null);
        }

        ContentAnalysisOutput output;
        try {
            output = objectMapper.readerFor(ContentAnalysisOutput.class)
                    .with(DeserializationFeature
                            .READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                    .readValue(json);
        } catch (JsonProcessingException | RuntimeException e) {
            throw parseFailure(
                    parseStage,
                    json,
                    allowedChunkIds,
                    "JSON 无法绑定为约定结构",
                    e);
        }

        return new ParsedResult(output, diagnostics);
    }

    public ContentAnalysisOutput validateAndEnrich(
            ContentAnalysisOutput output,
            List<SourceChunk> chunks,
            AnalysisResultValidationStage validationStage,
            AnalysisResponseDiagnostics diagnostics) {
        List<SourceChunk> safeChunks = chunks == null
                ? List.of() : List.copyOf(chunks);
        List<String> allowedChunkIds = safeChunks.stream()
                .map(SourceChunk::chunkId)
                .toList();
        List<AnalysisResultValidationError> errors =
                validate(output, safeChunks);
        if (!errors.isEmpty()) {
            throw new AnalysisResultValidationException(
                    validationStage,
                    errors,
                    allowedChunkIds,
                    diagnostics,
                    null);
        }
        enrich(output, safeChunks);
        return output;
    }

    private List<AnalysisResultValidationError> validate(
            ContentAnalysisOutput output,
            List<SourceChunk> chunks) {
        List<AnalysisResultValidationError> errors = new ArrayList<>();
        if (output == null) {
            errors.add(error(
                    MISSING_SUMMARY,
                    "JSON 根对象不能为空",
                    "$"));
            return errors;
        }
        if (output.getSummary() == null) {
            errors.add(error(
                    MISSING_SUMMARY,
                    "summary 必须存在",
                    "summary"));
        } else if (output.getSummary().getTopics() == null) {
            errors.add(error(
                    NULL_ARRAY,
                    "summary.topics 不得为 null",
                    "summary.topics"));
        }
        if (output.getKeyPoints() == null) {
            errors.add(error(
                    NULL_ARRAY,
                    "keyPoints 不得为 null",
                    "keyPoints"));
        }
        if (output.getChapters() == null) {
            errors.add(error(
                    NULL_ARRAY,
                    "chapters 不得为 null",
                    "chapters"));
        }
        if (output.getSpeechIssues() == null) {
            errors.add(error(
                    NULL_ARRAY,
                    "speechIssues 不得为 null",
                    "speechIssues"));
        }

        Map<String, SourceChunk> chunkMap = chunks.stream()
                .collect(Collectors.toMap(
                        SourceChunk::chunkId,
                        Function.identity(),
                        (left, right) -> left));
        Map<String, Integer> chunkIndexes = new HashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            chunkIndexes.put(chunks.get(index).chunkId(), index);
        }
        validateKeyPoints(output.getKeyPoints(), chunkMap, errors);
        validateChapters(output.getChapters(), chunkIndexes, errors);
        validateSpeechIssues(output.getSpeechIssues(), chunkMap, errors);
        return errors;
    }

    private void validateKeyPoints(
            List<ContentAnalysisOutput.KeyPoint> values,
            Map<String, SourceChunk> chunks,
            List<AnalysisResultValidationError> errors) {
        if (values == null) {
            return;
        }
        validateOrders(
                values.stream()
                        .map(value -> value == null
                                ? null : value.getOrder())
                        .toList(),
                "keyPoints",
                errors);
        for (int index = 0; index < values.size(); index++) {
            ContentAnalysisOutput.KeyPoint value = values.get(index);
            String field = "keyPoints[" + index + "]";
            if (value == null) {
                errors.add(error(
                        NULL_ARRAY_ITEM,
                        field + " 不能为空",
                        field));
                continue;
            }
            validateEvidence(
                    value.getEvidenceChunkIds(),
                    value.getEvidenceQuote(),
                    chunks,
                    field,
                    errors);
        }
    }

    private void validateChapters(
            List<ContentAnalysisOutput.Chapter> values,
            Map<String, Integer> chunkIndexes,
            List<AnalysisResultValidationError> errors) {
        if (values == null) {
            return;
        }
        validateOrders(
                values.stream()
                        .map(value -> value == null
                                ? null : value.getOrder())
                        .toList(),
                "chapters",
                errors);
        List<IndexedChapter> ordered = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            ContentAnalysisOutput.Chapter chapter = values.get(index);
            if (chapter == null) {
                String field = "chapters[" + index + "]";
                errors.add(error(
                        NULL_ARRAY_ITEM,
                        field + " 不能为空",
                        field));
            } else if (chapter.getOrder() != null) {
                ordered.add(new IndexedChapter(index, chapter));
            }
        }
        ordered.sort(Comparator.comparing(
                item -> item.chapter().getOrder()));

        int lastStart = -1;
        for (IndexedChapter item : ordered) {
            int index = item.index();
            ContentAnalysisOutput.Chapter chapter = item.chapter();
            String field = "chapters[" + index + "]";
            Integer start = chunkIndexes.get(chapter.getStartChunkId());
            Integer end = chunkIndexes.get(chapter.getEndChunkId());
            if (start == null) {
                errors.add(error(
                        UNKNOWN_CHUNK_ID,
                        field + ".startChunkId 不存在",
                        field + ".startChunkId"));
            }
            if (end == null) {
                errors.add(error(
                        UNKNOWN_CHUNK_ID,
                        field + ".endChunkId 不存在",
                        field + ".endChunkId"));
            }
            if (start != null && end != null && start > end) {
                errors.add(error(
                        CHAPTER_RANGE_REVERSED,
                        field + " 范围倒序",
                        field));
            }
            if (start != null && start < lastStart) {
                errors.add(error(
                        CHAPTER_ORDER_REVERSED,
                        "chapters 章节顺序倒序",
                        field + ".startChunkId"));
            }
            if (start != null) {
                lastStart = start;
            }
        }
    }

    private void validateSpeechIssues(
            List<ContentAnalysisOutput.SpeechIssue> values,
            Map<String, SourceChunk> chunks,
            List<AnalysisResultValidationError> errors) {
        if (values == null) {
            return;
        }
        validateOrders(
                values.stream()
                        .map(value -> value == null
                                ? null : value.getOrder())
                        .toList(),
                "speechIssues",
                errors);
        for (int index = 0; index < values.size(); index++) {
            ContentAnalysisOutput.SpeechIssue value = values.get(index);
            String field = "speechIssues[" + index + "]";
            if (value == null) {
                errors.add(error(
                        NULL_ARRAY_ITEM,
                        field + " 不能为空",
                        field));
                continue;
            }
            if (value.getType() == null) {
                errors.add(error(
                        INVALID_ISSUE_TYPE,
                        field + ".type 不在允许范围",
                        field + ".type"));
            }
            if (value.getSeverity() == null) {
                errors.add(error(
                        INVALID_SEVERITY,
                        field + ".severity 不在允许范围",
                        field + ".severity"));
            }
            validateEvidence(
                    value.getEvidenceChunkIds(),
                    value.getEvidenceQuote(),
                    chunks,
                    field,
                    errors);
        }
    }

    private void validateOrders(
            List<Integer> orders,
            String field,
            List<AnalysisResultValidationError> errors) {
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < orders.size(); index++) {
            Integer order = orders.get(index);
            String path = field + "[" + index + "].order";
            if (order == null || order <= 0) {
                errors.add(error(
                        INVALID_ORDER,
                        path + " 必须为正整数",
                        path));
            } else if (!seen.add(order)) {
                errors.add(error(
                        DUPLICATE_ORDER,
                        path + " 不得重复",
                        path));
            }
        }
    }

    private void validateEvidence(
            List<String> chunkIds,
            String quote,
            Map<String, SourceChunk> chunks,
            String field,
            List<AnalysisResultValidationError> errors) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            String path = field + ".evidenceChunkIds";
            errors.add(error(
                    MISSING_EVIDENCE_CHUNK_IDS,
                    path + " 不得为空",
                    path));
            return;
        }
        List<SourceChunk> referenced = new ArrayList<>();
        for (int index = 0; index < chunkIds.size(); index++) {
            String chunkId = chunkIds.get(index);
            SourceChunk chunk = chunks.get(chunkId);
            if (chunk == null) {
                String path = field + ".evidenceChunkIds[" + index + "]";
                errors.add(error(
                        UNKNOWN_CHUNK_ID,
                        path + " 引用了不存在的 chunkId",
                        path));
            } else {
                referenced.add(chunk);
            }
        }
        String quotePath = field + ".evidenceQuote";
        if (!StringUtils.hasText(quote)) {
            errors.add(error(
                    MISSING_EVIDENCE_QUOTE,
                    quotePath + " 不得为空",
                    quotePath));
        } else if (quote.length()
                > properties.getMaxEvidenceQuoteChars()) {
            errors.add(error(
                    EVIDENCE_QUOTE_TOO_LONG,
                    quotePath + " 超过 "
                            + properties.getMaxEvidenceQuoteChars()
                            + " 个字符",
                    quotePath));
        } else if (!referenced.isEmpty()
                && referenced.stream().noneMatch(chunk ->
                chunk.text() != null && chunk.text().contains(quote))) {
            errors.add(error(
                    EVIDENCE_QUOTE_NOT_FOUND,
                    quotePath + " 不属于引用的 SourceChunk",
                    quotePath));
        }
    }

    private void enrich(
            ContentAnalysisOutput output,
            List<SourceChunk> chunks) {
        Map<String, SourceChunk> chunkMap = chunks.stream()
                .collect(Collectors.toMap(
                        SourceChunk::chunkId,
                        Function.identity(),
                        (left, right) -> left));
        if (output.getKeyPoints() != null) {
            for (ContentAnalysisOutput.KeyPoint point
                    : output.getKeyPoints()) {
                applyEvidenceRange(
                        point.getEvidenceChunkIds(),
                        chunkMap,
                        point::setStartMs,
                        point::setEndMs,
                        point::setSourceSegmentOrders,
                        point::setTimePrecision);
            }
        }
        if (output.getSpeechIssues() != null) {
            for (ContentAnalysisOutput.SpeechIssue issue
                    : output.getSpeechIssues()) {
                applyEvidenceRange(
                        issue.getEvidenceChunkIds(),
                        chunkMap,
                        issue::setStartMs,
                        issue::setEndMs,
                        issue::setSourceSegmentOrders,
                        issue::setTimePrecision);
            }
        }
        if (output.getChapters() != null) {
            for (ContentAnalysisOutput.Chapter chapter
                    : output.getChapters()) {
                SourceChunk start = chunkMap.get(chapter.getStartChunkId());
                SourceChunk end = chunkMap.get(chapter.getEndChunkId());
                chapter.setStartMs(start.startMs());
                chapter.setEndMs(end.endMs());
                chapter.setTimePrecision(
                        start.timePrecision() == TimePrecision.SEGMENT
                                && end.timePrecision()
                                == TimePrecision.SEGMENT
                                ? TimePrecision.SEGMENT
                                : TimePrecision.TRANSCRIPT);
            }
        }
    }

    private void applyEvidenceRange(
            List<String> ids,
            Map<String, SourceChunk> chunks,
            java.util.function.Consumer<Long> startSetter,
            java.util.function.Consumer<Long> endSetter,
            java.util.function.Consumer<List<Integer>> ordersSetter,
            java.util.function.Consumer<TimePrecision> precisionSetter) {
        List<SourceChunk> referenced = ids.stream()
                .map(chunks::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        Long start = referenced.stream()
                .map(SourceChunk::startMs)
                .filter(java.util.Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);
        Long end = referenced.stream()
                .map(SourceChunk::endMs)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
        List<Integer> orders = new ArrayList<>(new LinkedHashSet<>(
                referenced.stream()
                        .map(SourceChunk::sourceSegmentOrder)
                        .filter(java.util.Objects::nonNull)
                        .sorted()
                        .toList()));
        TimePrecision precision = referenced.stream()
                .allMatch(chunk -> chunk.timePrecision()
                        == TimePrecision.SEGMENT)
                ? TimePrecision.SEGMENT : TimePrecision.TRANSCRIPT;
        startSetter.accept(start);
        endSetter.accept(end);
        ordersSetter.accept(orders);
        precisionSetter.accept(precision);
    }

    private AnalysisResultValidationException parseFailure(
            AnalysisResultValidationStage stage,
            String response,
            List<String> allowedChunkIds,
            String message,
            Throwable cause) {
        AnalysisResultValidationErrorCode code =
                stage == AnalysisResultValidationStage.REPAIR_PARSE
                        ? AnalysisResultValidationErrorCode
                        .REPAIR_JSON_PARSE_FAILED
                        : AnalysisResultValidationErrorCode
                        .JSON_PARSE_FAILED;
        return new AnalysisResultValidationException(
                stage,
                List.of(error(code, message, "$")),
                allowedChunkIds,
                AnalysisResponseDiagnostics.from(response, null),
                cause);
    }

    private AnalysisResultValidationError error(
            AnalysisResultValidationErrorCode code,
            String message,
            String field) {
        return new AnalysisResultValidationError(code, message, field);
    }

    private record IndexedChapter(
            int index,
            ContentAnalysisOutput.Chapter chapter) {
    }

    public record ParsedResult(
            ContentAnalysisOutput output,
            AnalysisResponseDiagnostics diagnostics) {
    }
}
