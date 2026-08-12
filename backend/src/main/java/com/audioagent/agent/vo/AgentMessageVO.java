package com.audioagent.agent.vo;

import com.audioagent.agent.entity.AgentMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AgentMessageVO {

    private String messageId;
    private String role;
    private String content;
    private String status;
    private Integer sequenceNo;
    private String replyToMessageId;
    private String clientRequestId;
    private String modelName;
    private String promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String failureCode;
    private String failureMessage;
    private List<AgentCitationVO> citations;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public static AgentMessageVO from(AgentMessage source,
                                      List<AgentCitationVO> citations) {
        return AgentMessageVO.builder()
                .messageId(source.getId().toString())
                .role(source.getRole().name())
                .content(source.getContent())
                .status(source.getStatus().name())
                .sequenceNo(source.getSequenceNo())
                .replyToMessageId(source.getReplyToMessageId() == null
                        ? null : source.getReplyToMessageId().toString())
                .clientRequestId(source.getClientRequestId())
                .modelName(source.getModelName())
                .promptVersion(source.getPromptVersion())
                .promptTokens(source.getPromptTokens())
                .completionTokens(source.getCompletionTokens())
                .totalTokens(source.getTotalTokens())
                .failureCode(source.getFailureCode())
                .failureMessage(source.getFailureMessage())
                .citations(citations == null ? List.of() : citations)
                .createdAt(source.getCreatedAt())
                .finishedAt(source.getFinishedAt())
                .build();
    }
}
