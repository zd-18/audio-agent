package com.audioagent.agent.service;

import com.audioagent.agent.dto.CreateAgentConversationRequest;
import com.audioagent.agent.vo.AgentConversationVO;
import com.audioagent.common.api.PageResult;

public interface AgentConversationService {

    AgentConversationVO create(Long userId,
                               CreateAgentConversationRequest request);

    PageResult<AgentConversationVO> list(Long userId, int current, int size,
                                         String transcriptId, String status);

    AgentConversationVO get(Long userId, String conversationId);
}
