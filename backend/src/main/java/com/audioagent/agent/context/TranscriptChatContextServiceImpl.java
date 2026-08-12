package com.audioagent.agent.context;

import com.audioagent.agent.config.AgentProperties;
import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptChatContextServiceImpl
        implements TranscriptChatContextService {

    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]+");
    private static final Pattern LATIN_NUMBER =
            Pattern.compile("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*");

    private final AudioTranscriptSegmentMapper segmentMapper;
    private final AgentProperties properties;

    @Override
    public TranscriptChatContext build(Long userId, Long transcriptId,
                                       String question) {
        List<AudioTranscriptSegment> all = segmentMapper.selectOwnedAll(
                        userId, transcriptId).stream()
                .filter(this::usable)
                .sorted(Comparator.comparing(
                        AudioTranscriptSegment::getSegmentOrder))
                .toList();
        if (all.isEmpty()) {
            throw new AgentExecutionException(
                    ErrorCode.AGENT_CONTEXT_BUILD_FAILED, false,
                    "Transcript has no usable segments");
        }

        List<AudioTranscriptSegment> selected;
        String allContent = render(all);
        if (all.size() <= properties.getContextMaxSegments()
                && allContent.length() <= properties.getContextMaxChars()) {
            selected = all;
        } else {
            selected = selectRelevant(all, question);
        }
        String content = render(selected);
        List<Integer> orders = selected.stream()
                .map(AudioTranscriptSegment::getSegmentOrder)
                .toList();
        log.info("Agent context built, transcriptId={}, queryLength={}, "
                        + "totalSegmentCount={}, selectedSegmentCount={}, "
                        + "selectedSegmentOrders={}, contextChars={}",
                transcriptId, question == null ? 0 : question.length(),
                all.size(), selected.size(), orders, content.length());
        return new TranscriptChatContext(content, selected, all.size(),
                orders, content.length());
    }

    private List<AudioTranscriptSegment> selectRelevant(
            List<AudioTranscriptSegment> all, String question) {
        Set<String> tokens = queryTokens(question);
        List<ScoredSegment> ranked = all.stream()
                .map(segment -> new ScoredSegment(segment,
                        score(segment.getText(), tokens)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSegment::score)
                        .reversed()
                        .thenComparing(item ->
                                item.segment().getSegmentOrder()))
                .toList();

        LinkedHashSet<AudioTranscriptSegment> candidates =
                new LinkedHashSet<>();
        if (ranked.isEmpty()) {
            candidates.add(all.getFirst());
        } else {
            for (ScoredSegment item : ranked) {
                int index = all.indexOf(item.segment());
                candidates.add(item.segment());
                if (index > 0) {
                    candidates.add(all.get(index - 1));
                }
                if (index + 1 < all.size()) {
                    candidates.add(all.get(index + 1));
                }
            }
        }

        List<AudioTranscriptSegment> selected = new ArrayList<>();
        int chars = 0;
        for (AudioTranscriptSegment candidate : candidates) {
            if (selected.size() >= properties.getContextMaxSegments()) {
                break;
            }
            int added = renderOne(candidate).length();
            if (chars + added <= properties.getContextMaxChars()) {
                selected.add(candidate);
                chars += added;
            }
        }
        selected.sort(Comparator.comparing(
                AudioTranscriptSegment::getSegmentOrder));
        return selected;
    }

    Set<String> queryTokens(String question) {
        Set<String> tokens = new HashSet<>();
        if (question == null) {
            return tokens;
        }
        Matcher hanMatcher = HAN.matcher(question);
        while (hanMatcher.find()) {
            String value = hanMatcher.group();
            if (value.length() == 1) {
                tokens.add(value);
            }
            for (int i = 0; i + 1 < value.length(); i++) {
                tokens.add(value.substring(i, i + 2));
            }
        }
        Matcher latinMatcher = LATIN_NUMBER.matcher(question);
        while (latinMatcher.find()) {
            tokens.add(latinMatcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private int score(String text, Set<String> tokens) {
        String normalized = text == null
                ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (normalized.contains(token)) {
                score += Math.max(1, token.length());
            }
        }
        return score;
    }

    private boolean usable(AudioTranscriptSegment segment) {
        return segment != null && segment.getId() != null
                && segment.getSegmentOrder() != null
                && segment.getStartMs() != null
                && segment.getEndMs() != null
                && segment.getText() != null
                && !segment.getText().isBlank();
    }

    private String render(List<AudioTranscriptSegment> segments) {
        StringBuilder result = new StringBuilder();
        for (AudioTranscriptSegment segment : segments) {
            result.append(renderOne(segment));
        }
        return result.toString();
    }

    private String renderOne(AudioTranscriptSegment segment) {
        return "[SEGMENT]\n"
                + "segmentId=" + segment.getId() + "\n"
                + "segmentOrder=" + segment.getSegmentOrder() + "\n"
                + "startMs=" + segment.getStartMs() + "\n"
                + "endMs=" + segment.getEndMs() + "\n"
                + "text=" + segment.getText() + "\n"
                + "[/SEGMENT]\n";
    }

    private record ScoredSegment(AudioTranscriptSegment segment, int score) {
    }
}
