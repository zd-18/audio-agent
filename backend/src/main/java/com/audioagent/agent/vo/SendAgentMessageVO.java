package com.audioagent.agent.vo;

import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendAgentMessageVO {
    private AgentMessageVO userMessage;
    private AgentMessageVO assistantMessage;
    private AgentProcessingWorkflowVO processingWorkflow;
}
