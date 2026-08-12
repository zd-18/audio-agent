package com.audioagent.contentanalysis.chunk;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.SourceChunk;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SourceChunker {

    private final DeepSeekProperties properties;

    public List<SourceChunk> build(
            List<AudioTranscriptSegment> segments,
            String fullText,
            Long durationMs) {
        List<SourceChunk> chunks = new ArrayList<>();
        if (segments != null && !segments.isEmpty()) {
            int fallbackOrder = 1;
            for (AudioTranscriptSegment segment : segments) {
                if (segment == null || !StringUtils.hasText(
                        segment.getText())) {
                    fallbackOrder++;
                    continue;
                }
                int order = segment.getSegmentOrder() == null
                        ? fallbackOrder : segment.getSegmentOrder();
                addSegmentChunks(chunks, order, segment.getStartMs(),
                        segment.getEndMs(), segment.getText(),
                        TimePrecision.SEGMENT);
                fallbackOrder++;
            }
        }
        if (chunks.isEmpty() && StringUtils.hasText(fullText)) {
            addSegmentChunks(chunks, 1, 0L, durationMs,
                    fullText, TimePrecision.TRANSCRIPT);
        }
        return List.copyOf(chunks);
    }

    private void addSegmentChunks(List<SourceChunk> target,
                                  int segmentOrder,
                                  Long startMs,
                                  Long endMs,
                                  String text,
                                  TimePrecision precision) {
        List<String> pieces = splitText(text.trim(),
                properties.getMaxChunkChars());
        for (int index = 0; index < pieces.size(); index++) {
            target.add(new SourceChunk(
                    "S" + segmentOrder + "-C" + (index + 1),
                    segmentOrder,
                    startMs,
                    endMs,
                    pieces.get(index),
                    precision));
        }
    }

    private List<String> splitText(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] sentences = text.split("(?<=[。！？；\\n])");
        for (String sentence : sentences) {
            if (sentence.isEmpty()) {
                continue;
            }
            if (sentence.length() > maxChars) {
                flush(chunks, current);
                for (int start = 0; start < sentence.length();
                     start += maxChars) {
                    chunks.add(sentence.substring(start,
                            Math.min(sentence.length(), start + maxChars)));
                }
            } else if (current.length() > 0
                    && current.length() + sentence.length() > maxChars) {
                flush(chunks, current);
                current.append(sentence);
            } else {
                current.append(sentence);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private void flush(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString());
        current.setLength(0);
    }
}
