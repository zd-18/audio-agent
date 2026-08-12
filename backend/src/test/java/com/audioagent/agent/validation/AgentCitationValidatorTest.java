package com.audioagent.agent.validation;

import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.agent.model.AgentModelResponse;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCitationValidatorTest {

    private final AgentCitationValidator validator =
            new AgentCitationValidator();

    @Test
    void exactQuoteUsesDatabaseTimingAndText() {
        AudioTranscriptSegment source = segment(
                301L, 7L, 81L, "这是数据库中的真实原文。", 100L, 900L);
        AgentModelResponse response = response(false,
                citation("301", "数据库中的真实原文"));

        var result = validator.validate(7L, 81L, response,
                List.of(source));

        assertEquals("数据库中的真实原文", result.getFirst().quote());
        assertEquals(100L, result.getFirst().startMs());
        assertEquals(900L, result.getFirst().endMs());
        assertEquals(AgentCitationValidator.MatchMethod.EXACT,
                result.getFirst().matchMethod());
    }

    @Test
    void normalizedPunctuationRecoversContinuousOriginalSubstring() {
        AudioTranscriptSegment source = segment(
                301L, 7L, 81L, "第一点：稳定、真实；可以定位。", 0L, 1000L);
        AgentModelResponse response = response(false,
                citation("301", "稳定 真实 可以定位"));

        var result = validator.validate(7L, 81L, response,
                List.of(source));

        assertEquals("稳定、真实；可以定位", result.getFirst().quote());
        assertEquals(AgentCitationValidator.MatchMethod.NORMALIZED,
                result.getFirst().matchMethod());
        assertTrue(source.getText().contains(result.getFirst().quote()));
    }

    @Test
    void segmentFromAnotherTranscriptIsRejected() {
        AgentModelResponse response = response(false,
                citation("301", "真实原文"));

        assertThrows(AgentExecutionException.class,
                () -> validator.validate(7L, 81L, response,
                        List.of(segment(301L, 7L, 82L,
                                "真实原文", 0L, 1000L))));
    }

    @Test
    void segmentFromAnotherUserIsRejected() {
        AgentModelResponse response = response(false,
                citation("301", "真实原文"));

        assertThrows(AgentExecutionException.class,
                () -> validator.validate(7L, 81L, response,
                        List.of(segment(301L, 8L, 81L,
                                "真实原文", 0L, 1000L))));
    }

    @Test
    void insufficientContextMayHaveNoCitation() {
        var result = validator.validate(7L, 81L,
                response(true), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void groundedAnswerWithoutCitationIsRejected() {
        assertThrows(AgentExecutionException.class,
                () -> validator.validate(7L, 81L,
                        response(false), List.of()));
    }

    private AgentModelResponse response(boolean insufficient,
                                        AgentModelResponse.Citation... citations) {
        AgentModelResponse response = new AgentModelResponse();
        response.setAnswer(insufficient
                ? "根据当前文字稿无法确定。" : "回答");
        response.setInsufficientContext(insufficient);
        response.setCitations(List.of(citations));
        return response;
    }

    private AgentModelResponse.Citation citation(String segmentId,
                                                  String quote) {
        AgentModelResponse.Citation citation =
                new AgentModelResponse.Citation();
        citation.setSegmentId(segmentId);
        citation.setQuote(quote);
        return citation;
    }

    private AudioTranscriptSegment segment(long id, long userId,
                                             long transcriptId, String text,
                                             long startMs, long endMs) {
        AudioTranscriptSegment segment = new AudioTranscriptSegment();
        segment.setId(id);
        segment.setUserId(userId);
        segment.setTranscriptId(transcriptId);
        segment.setSegmentOrder(1);
        segment.setStartMs(startMs);
        segment.setEndMs(endMs);
        segment.setText(text);
        return segment;
    }
}
