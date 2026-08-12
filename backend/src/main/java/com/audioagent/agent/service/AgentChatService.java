package com.audioagent.agent.service;

import com.audioagent.agent.dto.SendAgentMessageRequest;
import com.audioagent.agent.vo.AgentMessageVO;
import com.audioagent.agent.vo.SendAgentMessageVO;
import com.audioagent.common.api.PageResult;

public interface AgentChatService {

    PageResult<AgentMessageVO> listMessages(Long userId,
                                            String conversationId,
                                            int current, int size);

    SendAgentMessageVO send(Long userId, String conversationId,
                            SendAgentMessageRequest request);
}
