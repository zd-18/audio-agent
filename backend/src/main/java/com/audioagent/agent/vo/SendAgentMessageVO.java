package com.audioagent.agent.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendAgentMessageVO {
    private AgentMessageVO userMessage;
    private AgentMessageVO assistantMessage;
}
