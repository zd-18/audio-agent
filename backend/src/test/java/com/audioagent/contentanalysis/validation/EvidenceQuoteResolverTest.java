package com.audioagent.contentanalysis.validation;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.TimePrecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceQuoteResolverTest {

    private DeepSeekProperties properties;
    private EvidenceQuoteResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        resolver = new EvidenceQuoteResolver(properties);
    }

    @Test
    void keepsExactSourceSubstring() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "今天完成接口联调。明天开始回归测试。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "完成接口联调");

        var summary = resolver.resolve(output, List.of(chunk));

        assertEquals("完成接口联调",
                output.getKeyPoints().getFirst().getEvidenceQuote());
        assertEquals(EvidenceQuoteResolver.MatchMethod.EXACT,
                summary.items().getFirst().matchMethod());
    }

    @Test
    void trimsExtraOuterWhitespaceByReturningOriginalRange() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "今天完成接口联调。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "  完成接口联调  ");

        var summary = resolver.resolve(output, List.of(chunk));

        assertEquals("完成接口联调",
                output.getKeyPoints().getFirst().getEvidenceQuote());
        assertEquals(EvidenceQuoteResolver.MatchMethod.NORMALIZED,
                summary.items().getFirst().matchMethod());
    }

    @Test
    void mapsRepeatedWhitespaceNewlineAndTabBackToOriginal() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "项目计划\n今天   完成\t接口联调。后续回归。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "项目计划 今天 完成 接口联调");

        resolver.resolve(output, List.of(chunk));

        assertEquals("项目计划\n今天   完成\t接口联调",
                output.getKeyPoints().getFirst().getEvidenceQuote());
    }

    @Test
    void mapsChineseAndEnglishPunctuationBackToOriginal() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "他说：“开始吧”，大家同意。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"),
                "他说: \"开始吧\", 大家同意.");

        resolver.resolve(output, List.of(chunk));

        assertEquals("他说：“开始吧”，大家同意。",
                output.getKeyPoints().getFirst().getEvidenceQuote());
    }

    @Test
    void mapsFullWidthAndHalfWidthPunctuation() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "状态（完成）：百分之百！");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "状态(完成):百分之百!");

        resolver.resolve(output, List.of(chunk));

        assertEquals("状态（完成）：百分之百！",
                output.getKeyPoints().getFirst().getEvidenceQuote());
    }

    @Test
    void truncatesLongExactQuoteAsAContinuousSourceSubstring() {
        properties.setMaxEvidenceQuoteChars(8);
        SourceChunk chunk = chunk("S1-C1", 1,
                "一二三四五六七八九十，后续内容。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "一二三四五六七八九十");

        resolver.resolve(output, List.of(chunk));

        String quote = output.getKeyPoints().getFirst()
                .getEvidenceQuote();
        assertEquals("一二三四五六七八", quote);
        assertTrue(chunk.text().contains(quote));
    }

    @Test
    void fallsBackToCompleteSentenceFromAllowedChunk() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "第一句是真实原文。第二句也是真实原文。");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "模型概括了一个不存在的观点");

        var summary = resolver.resolve(output, List.of(chunk));

        assertEquals("第一句是真实原文。",
                output.getKeyPoints().getFirst().getEvidenceQuote());
        assertEquals(
                EvidenceQuoteResolver.MatchMethod
                        .SOURCE_CHUNK_EXCERPT_FALLBACK,
                summary.items().getFirst().matchMethod());
    }

    @Test
    void doesNotFallbackForUnknownChunkId() {
        ContentAnalysisOutput output = output(
                List.of("S9-C9"), "模型概括");

        var summary = resolver.resolve(
                output, List.of(chunk("S1-C1", 1, "真实原文。")));

        assertEquals("模型概括",
                output.getKeyPoints().getFirst().getEvidenceQuote());
        assertEquals(EvidenceQuoteResolver.MatchMethod.UNRESOLVED,
                summary.items().getFirst().matchMethod());
        assertEquals("SOURCE_CHUNK_NOT_ALLOWED",
                summary.items().getFirst().unresolvedReason());
    }

    @Test
    void doesNotFallbackToChunkOutsideCurrentTranscriptSet() {
        ContentAnalysisOutput output = output(
                List.of("OTHER-TRANSCRIPT-C1"), "模型概括");
        SourceChunk current = chunk("S1-C1", 1, "当前文字稿原文。");

        var summary = resolver.resolve(output, List.of(current));

        assertEquals(EvidenceQuoteResolver.MatchMethod.UNRESOLVED,
                summary.items().getFirst().matchMethod());
        assertFalse(current.text().contains(
                output.getKeyPoints().getFirst().getEvidenceQuote()));
    }

    @Test
    void doesNotFallbackToBlankChunkText() {
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "模型概括");

        var summary = resolver.resolve(
                output, List.of(chunk("S1-C1", 1, " \n\t ")));

        assertEquals(EvidenceQuoteResolver.MatchMethod.UNRESOLVED,
                summary.items().getFirst().matchMethod());
        assertEquals("SOURCE_CHUNK_TEXT_EMPTY",
                summary.items().getFirst().unresolvedReason());
    }

    @Test
    void everyResolvedQuoteIsAnExactSubstringOfSelectedChunk() {
        List<SourceChunk> chunks = List.of(
                chunk("S1-C1", 1, "中文，English text 混合。"),
                chunk("S2-C1", 2, "备用真实原文。"));
        ContentAnalysisOutput output = output(
                List.of("S1-C1", "S2-C1"),
                "中文, English text 混合.");

        var summary = resolver.resolve(output, chunks);
        var resolution = summary.items().getFirst();
        SourceChunk selected = chunks.stream()
                .filter(chunk -> chunk.chunkId().equals(
                        resolution.sourceChunkId()))
                .findFirst()
                .orElseThrow();

        assertTrue(selected.text().contains(
                output.getKeyPoints().getFirst().getEvidenceQuote()));
    }

    @Test
    void neverJoinsNonContiguousChunks() {
        List<SourceChunk> chunks = List.of(
                chunk("S1-C1", 1, "前半句"),
                chunk("S2-C1", 2, "后半句"));
        ContentAnalysisOutput output = output(
                List.of("S1-C1", "S2-C1"), "前半句后半句");

        resolver.resolve(output, chunks);

        assertEquals("前半句",
                output.getKeyPoints().getFirst().getEvidenceQuote());
        assertFalse(output.getKeyPoints().getFirst()
                .getEvidenceQuote().equals("前半句后半句"));
    }

    @Test
    void handlesChineseEnglishAndMixedTextWithoutCaseFuzzing() {
        SourceChunk chunk = chunk("S1-C1", 1,
                "版本 v2 已发布，API is stable.");
        ContentAnalysisOutput output = output(
                List.of("S1-C1"), "版本 v2 已发布, API is stable。");

        resolver.resolve(output, List.of(chunk));

        assertEquals("版本 v2 已发布，API is stable.",
                output.getKeyPoints().getFirst().getEvidenceQuote());
    }

    @Test
    void preservesSnowflakeChunkIdAndSegmentOrder() {
        String id = "2084176870403137537";
        SourceChunk chunk = chunk(id, 18, "第十八段真实原文。");
        ContentAnalysisOutput output = output(
                List.of(id), "第十八段真实原文");

        var summary = resolver.resolve(output, List.of(chunk));

        assertEquals(id, summary.items().getFirst().sourceChunkId());
        assertEquals(List.of(id), output.getKeyPoints().getFirst()
                .getEvidenceChunkIds());
        assertEquals(18, chunk.sourceSegmentOrder());
    }

    @Test
    void resolvesSpeechIssueEvidenceWithTheSameTruthfulnessRules() {
        SourceChunk chunk = chunk(
                "S1-C1", 1, "这个这个方案需要精简。");
        ContentAnalysisOutput output = new ContentAnalysisOutput();
        ContentAnalysisOutput.SpeechIssue issue =
                new ContentAnalysisOutput.SpeechIssue();
        issue.setOrder(1);
        issue.setEvidenceChunkIds(List.of("S1-C1"));
        issue.setEvidenceQuote("这个 这个方案需要精简.");
        output.setKeyPoints(List.of());
        output.setSpeechIssues(List.of(issue));

        var summary = resolver.resolve(output, List.of(chunk));

        assertEquals("这个这个方案需要精简。",
                issue.getEvidenceQuote());
        assertEquals("SPEECH_ISSUE",
                summary.items().getFirst().itemType());
        assertEquals(EvidenceQuoteResolver.MatchMethod.NORMALIZED,
                summary.items().getFirst().matchMethod());
    }

    private SourceChunk chunk(String id, int segmentOrder, String text) {
        return new SourceChunk(id, segmentOrder, 0L, 1_000L,
                text, TimePrecision.SEGMENT);
    }

    private ContentAnalysisOutput output(
            List<String> chunkIds, String quote) {
        ContentAnalysisOutput output = new ContentAnalysisOutput();
        ContentAnalysisOutput.KeyPoint point =
                new ContentAnalysisOutput.KeyPoint();
        point.setOrder(1);
        point.setEvidenceChunkIds(chunkIds);
        point.setEvidenceQuote(quote);
        output.setKeyPoints(List.of(point));
        output.setSpeechIssues(List.of());
        return output;
    }
}
