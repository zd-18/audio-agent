package com.audioagent.agent.workflow.service;

import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.entity.AgentMessage;
import com.audioagent.agent.workflow.model.AgentWorkflowPlanningResult;
import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;

import java.util.List;

public interface AgentProcessingWorkflowService {

    AgentWorkflowPlanningResult plan(Long userId,
                                     AgentConversation conversation,
                                     AgentMessage userMessage,
                                     AgentMessage assistantMessage,
                                     String requirement);

    AgentProcessingWorkflowVO confirm(Long userId, Long workflowId);

    AgentProcessingWorkflowVO get(Long userId, Long workflowId);

    AgentProcessingWorkflowVO findByUserMessage(Long userId,
                                                Long userMessageId);

    List<AgentProcessingWorkflowVO> listByConversation(
            Long userId, Long conversationId);
}
