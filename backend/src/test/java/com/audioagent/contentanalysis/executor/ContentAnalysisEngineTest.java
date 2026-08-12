package com.audioagent.contentanalysis.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.client.DeepSeekClient;
import com.audioagent.contentanalysis.client.DeepSeekResponse;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.SummaryStyle;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.audioagent.contentanalysis.prompt.ContentAnalysisPromptV1;
import com.audioagent.contentanalysis.prompt.ContentAnalysisRepairPromptV1;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationErrorCode;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationException;
import com.audioagent.contentanalysis.validation.AnalysisResultValidationStage;
import com.audioagent.contentanalysis.validation.AnalysisResultValidator;
import com.audioagent.contentanalysis.validation.EvidenceQuoteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContentAnalysisEngineTest {

    @Mock DeepSeekClient client;
    private ContentAnalysisEngine engine;
    private List<SourceChunk> chunks;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        DeepSeekProperties properties = new DeepSeekProperties();
        engine = new ContentAnalysisEngine(
                client,
                properties,
                new ContentAnalysisPromptV1(objectMapper, properties),
                new ContentAnalysisRepairPromptV1(
                        objectMapper, properties),
                new EvidenceQuoteResolver(properties),
                new AnalysisResultValidator(objectMapper, properties),
                objectMapper);
        chunks = List.of(new SourceChunk(
                "S1-C1", 1, 0L, 1_000L,
                "这是可引用的原文。", TimePrecision.SEGMENT));
    }

    @Test
    void validJsonSucceedsWithoutRepair() {
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(validJson()));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        assertEquals("一句摘要",
                result.output().getSummary().getOneSentence());
        assertEquals(1, result.callCount());
        verify(client).complete(anyString(), anyString());
    }

    @Test
    void locallyReplacesUnmatchedQuoteAndSkipsRepair(
            CapturedOutput output) {
        String paraphrased = validJson().replace(
                "可引用的原文", "模型概括但不是原文");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(paraphrased));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        assertEquals("这是可引用的原文。",
                result.output().getKeyPoints().getFirst()
                        .getEvidenceQuote());
        assertEquals(1, result.callCount());
        verify(client).complete(anyString(), anyString());
        assertTrue(output.getAll().contains(
                "matchMethod=SOURCE_CHUNK_EXCERPT_FALLBACK"));
        assertTrue(output.getAll().contains("fallbackCount=1"));
        assertTrue(output.getAll().contains("repairSkipped=true"));
        assertFalse(output.getAll().contains("这是可引用的原文。"));
    }

    @Test
    void locallyTruncatesLongExactQuoteAndSkipsRepair() {
        String source = "一".repeat(120) + "。";
        chunks = List.of(new SourceChunk(
                "S1-C1", 1, 0L, 1_000L,
                source, TimePrecision.SEGMENT));
        String tooLong = validJson().replace(
                "可引用的原文", "一".repeat(120));
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(tooLong));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        String quote = result.output().getKeyPoints().getFirst()
                .getEvidenceQuote();
        assertEquals(100, quote.length());
        assertTrue(source.contains(quote));
        assertEquals(1, result.callCount());
        verify(client).complete(anyString(), anyString());
    }

    @Test
    void callsRepairOnlyWhenLocalResolutionCannotBeSafe() {
        String missingQuote = validJson().replace(
                "\"evidenceQuote\": \"可引用的原文\"",
                "\"evidenceQuote\": \"\"");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(missingQuote),
                        response(validJson()));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        assertEquals(2, result.callCount());
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void normalizesRepairResultBeforeFinalValidation() {
        String formattingDifference = validJson().replace(
                "可引用的原文", "这是 可引用 的原文");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response("not-json"),
                        response(formattingDifference));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        assertEquals("这是可引用的原文",
                result.output().getKeyPoints().getFirst()
                        .getEvidenceQuote());
        assertEquals(2, result.callCount());
    }

    @Test
    void invalidJsonTriggersExactlyOneRepair() {
        when(client.complete(anyString(), anyString()))
                .thenReturn(response("not-json"),
                        response(validJson()));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        assertEquals(2, result.callCount());
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void invalidRepairBecomesNonRetryableResponseInvalid() {
        when(client.complete(anyString(), anyString()))
                .thenReturn(response("not-json"),
                        response("still-not-json"));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> engine.analyze(
                        EnumSet.allOf(AnalysisType.class),
                        SummaryStyle.STANDARD, "zh", 1_000L, chunks));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID,
                failure.getErrorCode());
        assertEquals(false, failure.isRetryable());
        AnalysisResultValidationException validation =
                (AnalysisResultValidationException) failure.getCause();
        assertEquals(AnalysisResultValidationStage.REPAIR_PARSE,
                validation.getStage());
        assertTrue(validation.getErrorCodes().contains(
                AnalysisResultValidationErrorCode
                        .REPAIR_JSON_PARSE_FAILED));
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void invalidRepairValidationKeepsRepairStageAndFieldCodes() {
        String invalidReference = validJson().replace(
                "\"evidenceChunkIds\": [\"S1-C1\"]",
                "\"evidenceChunkIds\": [\"S9-C9\"]");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response("not-json"),
                        response(invalidReference));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> engine.analyze(
                        EnumSet.allOf(AnalysisType.class),
                        SummaryStyle.STANDARD, "zh", 1_000L, chunks));

        AnalysisResultValidationException validation =
                (AnalysisResultValidationException) failure.getCause();
        assertEquals(AnalysisResultValidationStage.REPAIR_VALIDATION,
                validation.getStage());
        assertEquals(
                AnalysisResultValidationErrorCode
                        .REPAIR_RESULT_VALIDATION_FAILED,
                validation.getDiagnosticCode());
        assertTrue(validation.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.UNKNOWN_CHUNK_ID));
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void unknownChunkIdStillFailsWithAccuratePublicMessage() {
        String unknown = validJson().replace(
                "\"S1-C1\"", "\"S9-C9\"");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(unknown), response(unknown));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> engine.analyze(
                        EnumSet.allOf(AnalysisType.class),
                        SummaryStyle.STANDARD, "zh", 1_000L, chunks));

        assertEquals("智能分析中的原文引用无法验证，请重试。",
                failure.getMessage());
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void missingTopLevelStructureStillFailsAsIncomplete() {
        String incomplete = validJson().replace(
                "\"summary\": {", "\"missingSummary\": {");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(incomplete), response(incomplete));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> engine.analyze(
                        EnumSet.allOf(AnalysisType.class),
                        SummaryStyle.STANDARD, "zh", 1_000L, chunks));

        assertEquals("智能分析返回结果不完整，请重试。",
                failure.getMessage());
        verify(client, times(2)).complete(anyString(), anyString());
    }

    @Test
    void analyzesEighteenSegmentsAndKeepsQuoteInSelectedSource() {
        chunks = java.util.stream.IntStream.rangeClosed(1, 18)
                .mapToObj(index -> new SourceChunk(
                        "S" + index + "-C1", index,
                        index * 1_000L, (index + 1) * 1_000L,
                        "第" + index + "段真实原文。",
                        TimePrecision.SEGMENT))
                .toList();
        String responseJson = validJson()
                .replace("S1-C1", "S18-C1")
                .replace("可引用的原文", "第18段概括");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(responseJson));

        ContentAnalysisExecution result = engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 18_000L, chunks);

        String quote = result.output().getKeyPoints().getFirst()
                .getEvidenceQuote();
        assertTrue(chunks.get(17).text().contains(quote));
        assertEquals(List.of(18), result.output().getKeyPoints()
                .getFirst().getSourceSegmentOrders());
        assertEquals(1, result.callCount());
    }

    @Test
    void repairPromptContainsCodesFieldsAllowedIdsAndFullSchema() {
        String unknownChunk = validJson().replace(
                "\"evidenceChunkIds\": [\"S1-C1\"]",
                "\"evidenceChunkIds\": [\"S9-C9\"]");
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(unknownChunk),
                        response(validJson()));
        ArgumentCaptor<String> userPrompts =
                ArgumentCaptor.forClass(String.class);

        engine.analyze(
                EnumSet.allOf(AnalysisType.class),
                SummaryStyle.STANDARD, "zh", 1_000L, chunks);

        verify(client, times(2)).complete(
                anyString(), userPrompts.capture());
        String repair = userPrompts.getAllValues().get(1);
        assertTrue(repair.contains("UNKNOWN_CHUNK_ID"));
        assertTrue(repair.contains(
                "keyPoints[0].evidenceChunkIds[0]"));
        assertTrue(repair.contains("[\"S1-C1\"]"));
        assertTrue(repair.contains("\"summary\""));
        assertTrue(repair.contains("\"speechIssues\""));
        assertTrue(repair.contains(
                "已经合法的字段和值必须原样保留"));
        assertTrue(repair.contains("这是可引用的原文。"));
    }

    @Test
    void validationLogsContainDiagnosticsButNotResponseOrTranscript(
            CapturedOutput output) {
        String sensitiveResponse =
                "SENSITIVE_FULL_MODEL_RESPONSE_DO_NOT_LOG";
        when(client.complete(anyString(), anyString()))
                .thenReturn(response(sensitiveResponse),
                        response(sensitiveResponse));

        assertThrows(
                ContentAnalysisException.class,
                () -> engine.analyze(
                        EnumSet.allOf(AnalysisType.class),
                        SummaryStyle.STANDARD,
                        "zh",
                        1_000L,
                        chunks,
                        new ContentAnalysisDiagnosticContext(
                                2083031572163252226L,
                                2082751491419303938L,
                                "test-model",
                                "content-analysis-v1")));

        assertTrue(output.getAll().contains(
                "stage=REPAIR_PARSE"));
        assertTrue(output.getAll().contains(
                "errorCodes=[REPAIR_JSON_PARSE_FAILED]"));
        assertTrue(output.getAll().contains("responseSha256="));
        assertFalse(output.getAll().contains(sensitiveResponse));
        assertFalse(output.getAll().contains("这是可引用的原文。"));
    }

    private DeepSeekResponse response(String content) {
        return new DeepSeekResponse(
                content, "test-model", 10, 20, 30);
    }

    private String validJson() {
        return """
                {
                  "summary": {
                    "oneSentence": "一句摘要",
                    "detailed": "详细摘要",
                    "topics": ["主题"]
                  },
                  "keyPoints": [{
                    "order": 1,
                    "title": "观点",
                    "description": "说明",
                    "evidenceChunkIds": ["S1-C1"],
                    "evidenceQuote": "可引用的原文"
                  }],
                  "chapters": [{
                    "order": 1,
                    "title": "章节",
                    "summary": "摘要",
                    "startChunkId": "S1-C1",
                    "endChunkId": "S1-C1"
                  }],
                  "speechIssues": []
                }
                """;
    }
}
