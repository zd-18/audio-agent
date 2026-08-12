package com.audioagent.agent.context;

import com.audioagent.transcript.entity.AudioTranscriptSegment;

import java.util.List;

public record TranscriptChatContext(
        String content,
        List<AudioTranscriptSegment> segments,
        int totalSegmentCount,
        List<Integer> selectedSegmentOrders,
        int contextChars) {
}
