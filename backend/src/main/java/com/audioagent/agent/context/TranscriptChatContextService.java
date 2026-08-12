package com.audioagent.agent.context;

public interface TranscriptChatContextService {

    TranscriptChatContext build(Long userId, Long transcriptId,
                                String question);
}
