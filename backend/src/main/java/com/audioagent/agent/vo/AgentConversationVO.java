package com.audioagent.agent.vo;

import com.audioagent.agent.entity.AgentConversation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentConversationVO {

    private String conversationId;
    private String transcriptId;
    private String title;
    private String status;
    private String modelName;
    private String promptVersion;
    private String lastMessageId;
    private LocalDateTime lastMessageAt;
    private String audioFileName;
    private Long audioDurationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgentConversationVO from(AgentConversation source) {
        return detailed(source, null, null);
    }

    public static AgentConversationVO detailed(AgentConversation source,
                                               String audioFileName,
                                               Long audioDurationMs) {
        return AgentConversationVO.builder()
                .conversationId(source.getId().toString())
                .transcriptId(source.getTranscriptId().toString())
                .title(source.getTitle())
                .status(source.getStatus().name())
                .modelName(source.getModelName())
                .promptVersion(source.getPromptVersion())
                .lastMessageId(source.getLastMessageId() == null
                        ? null : source.getLastMessageId().toString())
                .lastMessageAt(source.getLastMessageAt())
                .audioFileName(audioFileName)
                .audioDurationMs(audioDurationMs)
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
