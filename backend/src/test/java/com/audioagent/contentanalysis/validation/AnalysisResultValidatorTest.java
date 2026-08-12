package com.audioagent.contentanalysis.validation;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisResultValidatorTest {

    private AnalysisResultValidator validator;
    private List<SourceChunk> chunks;

    @BeforeEach
    void setUp() {
        validator = new AnalysisResultValidator(
                new ObjectMapper(), new DeepSeekProperties());
        chunks = List.of(
                new SourceChunk("S1-C1", 1, 1_000L, 5_000L,
                        "我们需要先确定本周目标。", TimePrecision.SEGMENT),
                new SourceChunk("S1-C2", 1, 1_000L, 5_000L,
                        "本周目标是完成智能分析。", TimePrecision.SEGMENT));
    }

    @Test
    void validatesAndEnrichesOnlyFromSegmentRange() {
        var output = validator.parseValidateAndEnrich(validJson(), chunks);

        assertEquals(1_000L,
                output.getKeyPoints().getFirst().getStartMs());
        assertEquals(5_000L,
                output.getKeyPoints().getFirst().getEndMs());
        assertEquals(List.of(1),
                output.getKeyPoints().getFirst()
                        .getSourceSegmentOrders());
        assertEquals(TimePrecision.SEGMENT,
                output.getChapters().getFirst().getTimePrecision());
    }

    @Test
    void rejectsUnknownChunkReference() {
        String json = validJson().replace(
                "\"S1-C1\"", "\"S9-C9\"");

        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(json, chunks));

        assertEquals(AnalysisResultValidationStage.INITIAL_VALIDATION,
                failure.getStage());
        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.UNKNOWN_CHUNK_ID));
        assertTrue(failure.getInvalidFields().stream()
                .anyMatch(field -> field.contains("ChunkId")));
    }

    @Test
    void rejectsQuoteThatIsNotInReferencedText() {
        String json = validJson().replace(
                "本周目标", "完全不存在的引用");

        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(json, chunks));

        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode
                        .EVIDENCE_QUOTE_NOT_FOUND));
        assertTrue(failure.getInvalidFields().contains(
                "keyPoints[0].evidenceQuote"));
    }

    @Test
    void requiresResolverToRewriteWhitespaceDifferencesToSourceText() {
        String spaces = validJson().replace(
                "\"evidenceQuote\": \"本周目标\"",
                "\"evidenceQuote\": \"本周   目标\"");
        String newline = validJson().replace(
                "\"evidenceQuote\": \"本周目标\"",
                "\"evidenceQuote\": \"本周\\n目标\"");

        assertThrows(AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(spaces, chunks));
        assertThrows(AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(newline, chunks));
    }

    @Test
    void requiresResolverToRewritePunctuationDifferencesToSourceText() {
        List<SourceChunk> quoteChunks = List.of(
                new SourceChunk(
                        "S1-C1", 1, 1_000L, 5_000L,
                        "他说：“开始吧”。", TimePrecision.SEGMENT),
                chunks.get(1));
        String json = validJson().replace(
                "\"evidenceQuote\": \"本周目标\"",
                "\"evidenceQuote\": \"\\\"开始吧\\\"\"");

        assertThrows(AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(
                        json, quoteChunks));
    }

    @Test
    void allowsChapterWithSameStartAndEndChunk() {
        String json = validJson().replace(
                "\"endChunkId\": \"S1-C2\"",
                "\"endChunkId\": \"S1-C1\"");

        validator.parseValidateAndEnrich(json, chunks);
    }

    @Test
    void allowsChaptersWhoseChunksShareOneSegmentTimeRange() {
        String json = validJson().replace(
                """
                        {
                            "order": 1,
                            "title": "目标",
                            "summary": "确定并说明目标",
                            "startChunkId": "S1-C1",
                            "endChunkId": "S1-C2"
                          }
                        """.stripIndent().trim(),
                """
                        {
                            "order": 1,
                            "title": "目标一",
                            "summary": "先确定目标",
                            "startChunkId": "S1-C1",
                            "endChunkId": "S1-C1"
                          }, {
                            "order": 2,
                            "title": "目标二",
                            "summary": "说明目标",
                            "startChunkId": "S1-C2",
                            "endChunkId": "S1-C2"
                          }
                        """.stripIndent().trim());

        var output = validator.parseValidateAndEnrich(json, chunks);

        assertEquals(2, output.getChapters().size());
        assertEquals(output.getChapters().get(0).getStartMs(),
                output.getChapters().get(1).getStartMs());
        assertEquals(output.getChapters().get(0).getEndMs(),
                output.getChapters().get(1).getEndMs());
    }

    @Test
    void rejectsDuplicateOrderWithinOneArray() {
        String json = validJson().replace(
                """
                        {
                            "order": 1,
                            "title": "确定目标",
                            "description": "先确定本周目标",
                            "evidenceChunkIds": ["S1-C1"],
                            "evidenceQuote": "本周目标"
                          }
                        """.stripIndent().trim(),
                """
                        {
                            "order": 1,
                            "title": "确定目标",
                            "description": "先确定本周目标",
                            "evidenceChunkIds": ["S1-C1"],
                            "evidenceQuote": "本周目标"
                          }, {
                            "order": 1,
                            "title": "再次确定目标",
                            "description": "重复顺序",
                            "evidenceChunkIds": ["S1-C1"],
                            "evidenceQuote": "本周目标"
                          }
                        """.stripIndent().trim());

        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(json, chunks));

        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.DUPLICATE_ORDER));
        assertTrue(failure.getInvalidFields().contains(
                "keyPoints[1].order"));
    }

    @Test
    void allowsEachArrayToStartItsOwnOrderAtOne() {
        String json = validJson().replace(
                "\"speechIssues\": []",
                """
                        "speechIssues": [{
                          "order": 1,
                          "type": "REPETITION",
                          "severity": "LOW",
                          "description": "重复",
                          "evidenceChunkIds": ["S1-C2"],
                          "evidenceQuote": "本周目标",
                          "suggestion": "精简"
                        }]
                        """.stripIndent().trim());

        validator.parseValidateAndEnrich(json, chunks);
    }

    @Test
    void reportsInvalidEnumAsFieldLevelValidationError() {
        String json = validJson().replace(
                "\"speechIssues\": []",
                """
                        "speechIssues": [{
                          "order": 1,
                          "type": "NOT_A_REAL_TYPE",
                          "severity": "URGENT",
                          "description": "错误枚举",
                          "evidenceChunkIds": ["S1-C2"],
                          "evidenceQuote": "本周目标",
                          "suggestion": "精简"
                        }]
                        """.stripIndent().trim());

        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(json, chunks));

        assertEquals(AnalysisResultValidationStage.INITIAL_VALIDATION,
                failure.getStage());
        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.INVALID_ISSUE_TYPE));
        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.INVALID_SEVERITY));
    }

    @Test
    void reportsNullArrayWithoutNormalizingRequiredStructure() {
        String json = validJson().replace(
                "\"speechIssues\": []",
                "\"speechIssues\": null");

        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(json, chunks));

        assertTrue(failure.getErrorCodes().contains(
                AnalysisResultValidationErrorCode.NULL_ARRAY));
        assertTrue(failure.getInvalidFields().contains("speechIssues"));
    }

    @Test
    void separatesJsonParseFailureFromValidationFailure() {
        var failure = assertThrows(
                AnalysisResultValidationException.class,
                () -> validator.parseValidateAndEnrich(
                        "not-json", chunks));

        assertEquals(AnalysisResultValidationStage.INITIAL_PARSE,
                failure.getStage());
        assertEquals(AnalysisResultValidationErrorCode.JSON_PARSE_FAILED,
                failure.getDiagnosticCode());
        assertEquals(64,
                failure.getResponseDiagnostics()
                        .responseSha256().length());
    }

    private String validJson() {
        return """
                {
                  "summary": {
                    "oneSentence": "讨论本周目标",
                    "detailed": "团队明确了智能分析目标。",
                    "topics": ["目标"]
                  },
                  "keyPoints": [{
                    "order": 1,
                    "title": "确定目标",
                    "description": "先确定本周目标",
                    "evidenceChunkIds": ["S1-C1"],
                    "evidenceQuote": "本周目标"
                  }],
                  "chapters": [{
                    "order": 1,
                    "title": "目标",
                    "summary": "确定并说明目标",
                    "startChunkId": "S1-C1",
                    "endChunkId": "S1-C2"
                  }],
                  "speechIssues": []
                }
                """;
    }
}
