package com.audioagent.agent.context;

import com.audioagent.agent.config.AgentProperties;
import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.audioagent.transcript.mapper.AudioTranscriptSegmentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TranscriptChatContextServiceImplTest {

    private final AudioTranscriptSegmentMapper mapper =
            mock(AudioTranscriptSegmentMapper.class);
    private final AgentProperties properties = new AgentProperties();
    private final TranscriptChatContextServiceImpl service =
            new TranscriptChatContextServiceImpl(mapper, properties);

    @Test
    void shortTranscriptUsesEverySegmentAndKeepsStableIds() {
        when(mapper.selectOwnedAll(7L, 81L)).thenReturn(List.of(
                segment(301L, 3, "第三段"),
                segment(101L, 1, "第一段"),
                segment(201L, 2, "第二段")));

        TranscriptChatContext result = service.build(
                7L, 81L, "主要观点是什么");

        assertEquals(List.of(1, 2, 3),
                result.selectedSegmentOrders());
        assertTrue(result.content().contains("segmentId=101"));
        assertTrue(result.content().contains("segmentId=201"));
        assertTrue(result.content().contains("segmentId=301"));
    }

    @Test
    void longTranscriptSelectsChineseHitAndItsNeighborsThenSorts() {
        properties.setContextMaxChars(1000);
        properties.setContextMaxSegments(3);
        List<AudioTranscriptSegment> segments = List.of(
                segment(101L, 1, "无关甲".repeat(70)),
                segment(201L, 2, "相邻前文".repeat(35)),
                segment(301L, 3, "量子计算核心观点" + "说明".repeat(65)),
                segment(401L, 4, "相邻后文".repeat(35)),
                segment(501L, 5, "无关乙".repeat(70)));
        when(mapper.selectOwnedAll(7L, 81L)).thenReturn(segments);

        TranscriptChatContext result = service.build(
                7L, 81L, "量子计算的观点是什么");

        assertEquals(List.of(2, 3, 4),
                result.selectedSegmentOrders());
        assertTrue(result.contextChars() <= 1000);
    }

    @Test
    void englishAndNumericKeywordsSelectMatchingSegment() {
        properties.setContextMaxChars(500);
        properties.setContextMaxSegments(1);
        when(mapper.selectOwnedAll(7L, 81L)).thenReturn(List.of(
                segment(101L, 1, "普通内容".repeat(80)),
                segment(201L, 2, "Model GPT-5 achieved 92 points "
                        + "detail ".repeat(30)),
                segment(301L, 3, "其他内容".repeat(80))));

        TranscriptChatContext result = service.build(
                7L, 81L, "How did GPT-5 reach 92 points?");

        assertEquals(List.of(2), result.selectedSegmentOrders());
        assertTrue(result.content().contains("segmentId=201"));
    }

    private AudioTranscriptSegment segment(long id, int order,
                                             String text) {
        AudioTranscriptSegment segment = new AudioTranscriptSegment();
        segment.setId(id);
        segment.setUserId(7L);
        segment.setTranscriptId(81L);
        segment.setSegmentOrder(order);
        segment.setStartMs((long) (order - 1) * 1000);
        segment.setEndMs((long) order * 1000);
        segment.setText(text);
        return segment;
    }
}
