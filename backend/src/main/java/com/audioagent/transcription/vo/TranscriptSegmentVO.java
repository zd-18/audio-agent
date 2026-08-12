package com.audioagent.transcription.vo;

import com.audioagent.transcript.entity.AudioTranscriptSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentVO {
    private String segmentId;
    /**
     * Backward-compatible field used by the current transcript UI.
     */
    private Integer order;
    /**
     * Stable reference field for downstream Agent citations.
     */
    private Integer segmentOrder;
    private Long startMs;
    private Long endMs;
    private String speaker;
    private String text;
    private BigDecimal confidence;

    public static TranscriptSegmentVO from(AudioTranscriptSegment segment) {
        return TranscriptSegmentVO.builder()
                .segmentId(segment.getId().toString())
                .order(segment.getSegmentOrder())
                .segmentOrder(segment.getSegmentOrder())
                .startMs(segment.getStartMs())
                .endMs(segment.getEndMs())
                .speaker(segment.getSpeakerLabel())
                .text(segment.getText())
                .confidence(segment.getConfidence())
                .build();
    }
}
