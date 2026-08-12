package com.audioagent.contentanalysis.chunk;

import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.model.TimePrecision;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceChunkerTest {

    @Test
    void splitsLongSegmentWithoutInventingTimestamps() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setMaxChunkChars(6);
        SourceChunker chunker = new SourceChunker(properties);
        AudioTranscriptSegment segment = new AudioTranscriptSegment();
        segment.setSegmentOrder(3);
        segment.setStartMs(12_000L);
        segment.setEndMs(21_000L);
        segment.setText("第一句话。第二句话！第三句话？");

        var chunks = chunker.build(List.of(segment),
                segment.getText(), 21_000L);

        assertTrue(chunks.size() > 1);
        assertEquals("S3-C1", chunks.getFirst().chunkId());
        assertTrue(chunks.stream().allMatch(
                chunk -> chunk.sourceSegmentOrder() == 3));
        assertTrue(chunks.stream().allMatch(
                chunk -> chunk.startMs() == 12_000L));
        assertTrue(chunks.stream().allMatch(
                chunk -> chunk.endMs() == 21_000L));
        assertTrue(chunks.stream().allMatch(
                chunk -> chunk.timePrecision()
                        == TimePrecision.SEGMENT));
    }

    @Test
    void keepsDifferentSourceSegmentRanges() {
        DeepSeekProperties properties = new DeepSeekProperties();
        SourceChunker chunker = new SourceChunker(properties);
        AudioTranscriptSegment first = segment(
                1, 0L, 1_000L, "第一段");
        AudioTranscriptSegment second = segment(
                2, 1_000L, 2_000L, "第二段");

        var chunks = chunker.build(List.of(first, second),
                "第一段第二段", 2_000L);

        assertEquals(2, chunks.size());
        assertEquals(0L, chunks.get(0).startMs());
        assertEquals(1_000L, chunks.get(0).endMs());
        assertEquals(1_000L, chunks.get(1).startMs());
        assertEquals(2_000L, chunks.get(1).endMs());
    }

    private AudioTranscriptSegment segment(
            int order, long start, long end, String text) {
        AudioTranscriptSegment segment = new AudioTranscriptSegment();
        segment.setSegmentOrder(order);
        segment.setStartMs(start);
        segment.setEndMs(end);
        segment.setText(text);
        return segment;
    }
}
