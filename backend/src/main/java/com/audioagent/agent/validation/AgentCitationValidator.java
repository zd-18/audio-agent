package com.audioagent.agent.validation;

import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.agent.model.AgentModelResponse;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class AgentCitationValidator {

    public List<ValidatedCitation> validate(
            Long userId, Long transcriptId,
            AgentModelResponse response,
            List<AudioTranscriptSegment> contextSegments) {
        Map<Long, AudioTranscriptSegment> sources = new LinkedHashMap<>();
        for (AudioTranscriptSegment segment : contextSegments) {
            if (segment != null && userId.equals(segment.getUserId())
                    && transcriptId.equals(segment.getTranscriptId())) {
                sources.put(segment.getId(), segment);
            }
        }

        List<ValidatedCitation> result = new ArrayList<>();
        int exact = 0;
        int normalized = 0;
        int unresolved = 0;
        for (AgentModelResponse.Citation citation
                : response.getCitations()) {
            try {
                ValidatedCitation validated = resolve(citation, sources);
                result.add(validated);
                if (validated.matchMethod() == MatchMethod.EXACT) {
                    exact++;
                } else {
                    normalized++;
                }
            } catch (AgentExecutionException e) {
                unresolved++;
                log.info("Agent citation validation failed, transcriptId={}, "
                                + "citationIndex={}, failureCode={}",
                        transcriptId, result.size() + unresolved,
                        e.getErrorCode().name());
                throw e;
            }
        }
        if (!Boolean.TRUE.equals(response.getInsufficientContext())
                && result.isEmpty()) {
            throw invalid("A grounded answer requires a valid citation");
        }
        log.info("Agent citations validated, transcriptId={}, "
                        + "citationCount={}, exactMatchCount={}, "
                        + "normalizedMatchCount={}, unresolvedCount={}",
                transcriptId, result.size(), exact, normalized, unresolved);
        return result;
    }

    private ValidatedCitation resolve(
            AgentModelResponse.Citation citation,
            Map<Long, AudioTranscriptSegment> sources) {
        if (citation == null || citation.getSegmentId() == null
                || !citation.getSegmentId().matches("[1-9]\\d{0,18}")
                || citation.getQuote() == null
                || citation.getQuote().isBlank()) {
            throw invalid("Citation structure is invalid");
        }
        Long segmentId;
        try {
            segmentId = Long.valueOf(citation.getSegmentId());
        } catch (NumberFormatException e) {
            throw invalid("Citation segmentId is invalid");
        }
        AudioTranscriptSegment source = sources.get(segmentId);
        if (source == null || source.getText() == null
                || source.getText().isBlank()) {
            throw invalid("Citation segment is outside the supplied transcript context");
        }
        String quote = citation.getQuote();
        int exactIndex = source.getText().indexOf(quote);
        if (exactIndex >= 0) {
            return validated(source, quote, MatchMethod.EXACT);
        }
        String recovered = recoverNormalized(source.getText(), quote);
        if (recovered == null) {
            throw invalid("Citation quote is not present in the source segment");
        }
        return validated(source, recovered, MatchMethod.NORMALIZED);
    }

    private ValidatedCitation validated(AudioTranscriptSegment source,
                                        String quote,
                                        MatchMethod method) {
        if (quote.length() > 1000) {
            throw invalid("Citation quote exceeds the storage limit");
        }
        return new ValidatedCitation(source.getId(),
                source.getSegmentOrder(), source.getStartMs(),
                source.getEndMs(), quote, method);
    }

    private String recoverNormalized(String source, String quote) {
        NormalizedText normalizedSource = normalize(source, true);
        NormalizedText normalizedQuote = normalize(quote, false);
        if (normalizedQuote.value().isEmpty()) {
            return null;
        }
        int index = normalizedSource.value()
                .indexOf(normalizedQuote.value());
        if (index < 0) {
            return null;
        }
        int originalStart = normalizedSource.originalIndexes().get(index);
        int originalEnd = normalizedSource.originalIndexes().get(
                index + normalizedQuote.value().length() - 1) + 1;
        return source.substring(originalStart, originalEnd);
    }

    private NormalizedText normalize(String value, boolean keepIndexes) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> indexes = new ArrayList<>();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                String lowered = new String(Character.toChars(codePoint))
                        .toLowerCase(Locale.ROOT);
                normalized.append(lowered);
                if (keepIndexes) {
                    for (int i = 0; i < lowered.length(); i++) {
                        indexes.add(offset);
                    }
                }
            }
            offset += Character.charCount(codePoint);
        }
        return new NormalizedText(normalized.toString(), indexes);
    }

    private AgentExecutionException invalid(String message) {
        return new AgentExecutionException(
                ErrorCode.AGENT_CITATION_INVALID, false, message);
    }

    public enum MatchMethod {
        EXACT,
        NORMALIZED
    }

    public record ValidatedCitation(
            Long segmentId,
            Integer segmentOrder,
            Long startMs,
            Long endMs,
            String quote,
            MatchMethod matchMethod) {
    }

    private record NormalizedText(String value,
                                  List<Integer> originalIndexes) {
    }
}
