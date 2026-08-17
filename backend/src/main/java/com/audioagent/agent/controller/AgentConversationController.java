package com.audioagent.agent.controller;

import com.audioagent.agent.dto.CreateAgentConversationRequest;
import com.audioagent.agent.dto.SendAgentMessageRequest;
import com.audioagent.agent.service.AgentChatService;
import com.audioagent.agent.service.AgentConversationService;
import com.audioagent.agent.vo.AgentConversationVO;
import com.audioagent.agent.vo.AgentMessageVO;
import com.audioagent.agent.vo.SendAgentMessageVO;
import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/conversations")
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentConversationService conversationService;
    private final AgentChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<AgentConversationVO> create(
            @Valid @RequestBody CreateAgentConversationRequest request) {
        return ApiResponse.success(conversationService.create(
                currentUserProvider.requireUserId(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<AgentConversationVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String transcriptId,
            @RequestParam(required = false) String audioFileId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(conversationService.list(
                currentUserProvider.requireUserId(), current, size,
                transcriptId, audioFileId, status));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<AgentConversationVO> get(
            @PathVariable String conversationId) {
        return ApiResponse.success(conversationService.get(
                currentUserProvider.requireUserId(), conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResult<AgentMessageVO>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(chatService.listMessages(
                currentUserProvider.requireUserId(), conversationId,
                current, size));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<SendAgentMessageVO> send(
            @PathVariable String conversationId,
            @Valid @RequestBody SendAgentMessageRequest request) {
        return ApiResponse.success(chatService.send(
                currentUserProvider.requireUserId(), conversationId,
                request));
    }
}
