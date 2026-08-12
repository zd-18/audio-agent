package com.audioagent.contentanalysis.validation;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.ContentAnalysisOutput;
import com.audioagent.contentanalysis.model.SourceChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EvidenceQuoteResolver {

    private final DeepSeekProperties properties;

    public ResolutionSummary resolve(
            ContentAnalysisOutput output,
            List<SourceChunk> allowedChunks) {
        if (output == null) {
            return ResolutionSummary.empty();
        }
        Map<String, SourceChunk> chunks = new LinkedHashMap<>();
        if (allowedChunks != null) {
            for (SourceChunk chunk : allowedChunks) {
                if (chunk != null && chunk.chunkId() != null) {
                    chunks.putIfAbsent(chunk.chunkId(), chunk);
                }
            }
        }

        List<Resolution> resolutions = new ArrayList<>();
        if (output.getKeyPoints() != null) {
            for (int index = 0;
                 index < output.getKeyPoints().size(); index++) {
                ContentAnalysisOutput.KeyPoint item =
                        output.getKeyPoints().get(index);
                if (item != null) {
                    Resolution resolution = resolveOne(
                            "KEY_POINT", index,
                            item.getEvidenceChunkIds(),
                            item.getEvidenceQuote(), chunks);
                    if (resolution.resolvedQuote() != null) {
                        item.setEvidenceQuote(resolution.resolvedQuote());
                    }
                    resolutions.add(resolution.withoutQuote());
                }
            }
        }
        if (output.getSpeechIssues() != null) {
            for (int index = 0;
                 index < output.getSpeechIssues().size(); index++) {
                ContentAnalysisOutput.SpeechIssue item =
                        output.getSpeechIssues().get(index);
                if (item != null) {
                    Resolution resolution = resolveOne(
                            "SPEECH_ISSUE", index,
                            item.getEvidenceChunkIds(),
                            item.getEvidenceQuote(), chunks);
                    if (resolution.resolvedQuote() != null) {
                        item.setEvidenceQuote(resolution.resolvedQuote());
                    }
                    resolutions.add(resolution.withoutQuote());
                }
            }
        }
        return new ResolutionSummary(List.copyOf(resolutions));
    }

    private Resolution resolveOne(
            String itemType,
            int itemIndex,
            List<String> chunkIds,
            String quote,
            Map<String, SourceChunk> allowedChunks) {
        int originalLength = quote == null ? 0 : quote.length();
        if (chunkIds == null || chunkIds.isEmpty()) {
            return unresolved(itemType, itemIndex, null,
                    originalLength, "MISSING_SOURCE_CHUNK_ID");
        }
        if (!StringUtils.hasText(quote)) {
            return unresolved(itemType, itemIndex, chunkIds.getFirst(),
                    originalLength, "MISSING_EVIDENCE_QUOTE");
        }

        List<SourceChunk> referenced = chunkIds.stream()
                .map(allowedChunks::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (referenced.isEmpty()) {
            return unresolved(itemType, itemIndex, chunkIds.getFirst(),
                    originalLength, "SOURCE_CHUNK_NOT_ALLOWED");
        }

        int maximum = properties.getMaxEvidenceQuoteChars();
        for (SourceChunk chunk : referenced) {
            if (chunk.text() == null) {
                continue;
            }
            int exactStart = chunk.text().indexOf(quote);
            if (exactStart >= 0) {
                String resolved = boundedSubstring(
                        chunk.text(), exactStart,
                        exactStart + quote.length(), maximum);
                return resolved(itemType, itemIndex, chunk.chunkId(),
                        originalLength, MatchMethod.EXACT, resolved);
            }
        }

        NormalizedText normalizedQuote = normalize(quote);
        if (!normalizedQuote.value().isEmpty()) {
            for (SourceChunk chunk : referenced) {
                if (!StringUtils.hasText(chunk.text())) {
                    continue;
                }
                NormalizedText normalizedChunk = normalize(chunk.text());
                int normalizedStart = normalizedChunk.value()
                        .indexOf(normalizedQuote.value());
                if (normalizedStart >= 0) {
                    int normalizedEnd = normalizedStart
                            + normalizedQuote.value().length();
                    int originalStart = normalizedChunk.characters()
                            .get(normalizedStart).originalStart();
                    int originalEnd = normalizedChunk.characters()
                            .get(normalizedEnd - 1).originalEnd();
                    String resolved = boundedSubstring(
                            chunk.text(), originalStart,
                            originalEnd, maximum);
                    return resolved(
                            itemType, itemIndex, chunk.chunkId(),
                            originalLength, MatchMethod.NORMALIZED,
                            resolved);
                }
            }
        }

        SourceChunk fallbackChunk = referenced.stream()
                .filter(chunk -> StringUtils.hasText(chunk.text()))
                .findFirst()
                .orElse(null);
        if (fallbackChunk == null) {
            return unresolved(itemType, itemIndex,
                    referenced.getFirst().chunkId(), originalLength,
                    "SOURCE_CHUNK_TEXT_EMPTY");
        }
        String excerpt = excerpt(
                fallbackChunk.text(), maximum);
        if (!StringUtils.hasText(excerpt)) {
            return unresolved(itemType, itemIndex,
                    fallbackChunk.chunkId(), originalLength,
                    "SOURCE_CHUNK_TEXT_EMPTY");
        }
        return resolved(itemType, itemIndex, fallbackChunk.chunkId(),
                originalLength,
                MatchMethod.SOURCE_CHUNK_EXCERPT_FALLBACK,
                excerpt);
    }

    private Resolution resolved(
            String itemType,
            int itemIndex,
            String sourceChunkId,
            int originalLength,
            MatchMethod method,
            String quote) {
        return new Resolution(itemType, itemIndex, sourceChunkId,
                originalLength, method, quote.length(), null, quote);
    }

    private Resolution unresolved(
            String itemType,
            int itemIndex,
            String sourceChunkId,
            int originalLength,
            String reason) {
        return new Resolution(itemType, itemIndex, sourceChunkId,
                originalLength, MatchMethod.UNRESOLVED, 0,
                reason, null);
    }

    private String boundedSubstring(
            String source, int start, int end, int maximum) {
        int boundedEnd = Math.min(end, start + maximum);
        if (boundedEnd < source.length()
                && boundedEnd > start
                && Character.isHighSurrogate(
                source.charAt(boundedEnd - 1))
                && Character.isLowSurrogate(
                source.charAt(boundedEnd))) {
            boundedEnd--;
        }
        return source.substring(start, boundedEnd);
    }

    private String excerpt(String source, int maximum) {
        int start = 0;
        while (start < source.length()
                && isWhitespace(source.charAt(start))) {
            start++;
        }
        if (start >= source.length()) {
            return "";
        }
        int hardEnd = Math.min(source.length(), start + maximum);
        for (int index = start; index < hardEnd; index++) {
            if (isSentenceEnd(source.charAt(index))) {
                return boundedSubstring(
                        source, start, index + 1, maximum);
            }
        }
        return boundedSubstring(source, start, source.length(), maximum);
    }

    private boolean isSentenceEnd(char value) {
        return value == '。' || value == '！' || value == '？'
                || value == '；' || value == '.' || value == '!'
                || value == '?' || value == ';' || value == '\n';
    }

    private NormalizedText normalize(String source) {
        List<MappedCharacter> raw = new ArrayList<>();
        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            int nextOffset = offset + Character.charCount(codePoint);
            String compatible = Normalizer.normalize(
                    new String(Character.toChars(codePoint)),
                    Normalizer.Form.NFKC);
            for (int index = 0; index < compatible.length(); index++) {
                char value = compatible.charAt(index);
                String canonical = canonical(value);
                for (int canonicalIndex = 0;
                     canonicalIndex < canonical.length();
                     canonicalIndex++) {
                    char canonicalValue = canonical.charAt(canonicalIndex);
                    if (isWhitespace(canonicalValue)) {
                        canonicalValue = ' ';
                    }
                    raw.add(new MappedCharacter(
                            canonicalValue, offset, nextOffset));
                }
            }
            offset = nextOffset;
        }

        List<MappedCharacter> collapsed = new ArrayList<>();
        for (MappedCharacter character : raw) {
            if (character.value() == ' '
                    && !collapsed.isEmpty()
                    && collapsed.getLast().value() == ' ') {
                MappedCharacter previous = collapsed.removeLast();
                collapsed.add(new MappedCharacter(
                        ' ', previous.originalStart(),
                        character.originalEnd()));
            } else {
                collapsed.add(character);
            }
        }
        while (!collapsed.isEmpty()
                && collapsed.getFirst().value() == ' ') {
            collapsed.removeFirst();
        }
        while (!collapsed.isEmpty()
                && collapsed.getLast().value() == ' ') {
            collapsed.removeLast();
        }

        List<MappedCharacter> normalized = new ArrayList<>();
        for (int index = 0; index < collapsed.size(); index++) {
            MappedCharacter character = collapsed.get(index);
            if (character.value() == ' ' && index > 0
                    && index + 1 < collapsed.size()) {
                char previous = collapsed.get(index - 1).value();
                char next = collapsed.get(index + 1).value();
                if ((isHan(previous) && isHan(next))
                        || isPunctuation(previous)
                        || isPunctuation(next)) {
                    continue;
                }
            }
            normalized.add(character);
        }
        StringBuilder value = new StringBuilder(normalized.size());
        normalized.forEach(character -> value.append(character.value()));
        return new NormalizedText(value.toString(), List.copyOf(normalized));
    }

    private String canonical(char value) {
        return switch (value) {
            case '“', '”', '„', '‟' -> "\"";
            case '‘', '’', '‚', '‛' -> "'";
            case '。' -> ".";
            case '、' -> ",";
            case '…' -> "...";
            case '—', '–', '‐', '‑' -> "-";
            default -> String.valueOf(value);
        };
    }

    private boolean isWhitespace(char value) {
        return Character.isWhitespace(value)
                || Character.isSpaceChar(value);
    }

    private boolean isHan(char value) {
        return Character.UnicodeScript.of(value)
                == Character.UnicodeScript.HAN;
    }

    private boolean isPunctuation(char value) {
        return ",.;:!?()[]{}\"'-/".indexOf(value) >= 0;
    }

    public enum MatchMethod {
        EXACT,
        NORMALIZED,
        SOURCE_CHUNK_EXCERPT_FALLBACK,
        UNRESOLVED
    }

    public record Resolution(
            String itemType,
            int itemIndex,
            String sourceChunkId,
            int originalQuoteLength,
            MatchMethod matchMethod,
            int finalQuoteLength,
            String unresolvedReason,
            String resolvedQuote) {

        private Resolution withoutQuote() {
            return new Resolution(
                    itemType, itemIndex, sourceChunkId,
                    originalQuoteLength, matchMethod,
                    finalQuoteLength, unresolvedReason, null);
        }
    }

    public record ResolutionSummary(List<Resolution> items) {

        public static ResolutionSummary empty() {
            return new ResolutionSummary(List.of());
        }

        public long exactMatchCount() {
            return count(MatchMethod.EXACT);
        }

        public long normalizedMatchCount() {
            return count(MatchMethod.NORMALIZED);
        }

        public long fallbackCount() {
            return count(MatchMethod.SOURCE_CHUNK_EXCERPT_FALLBACK);
        }

        public long unresolvedCount() {
            return count(MatchMethod.UNRESOLVED);
        }

        private long count(MatchMethod method) {
            return items.stream()
                    .filter(item -> item.matchMethod() == method)
                    .count();
        }
    }

    private record MappedCharacter(
            char value,
            int originalStart,
            int originalEnd) {
    }

    private record NormalizedText(
            String value,
            List<MappedCharacter> characters) {
    }
}
