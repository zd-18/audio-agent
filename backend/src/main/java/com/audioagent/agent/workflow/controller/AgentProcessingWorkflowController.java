package com.audioagent.agent.workflow.controller;

import com.audioagent.agent.workflow.service.AgentProcessingWorkflowService;
import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;
import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentProcessingWorkflowController {

    private final AgentProcessingWorkflowService workflowService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/conversations/{conversationId}/processing-workflows")
    public ApiResponse<List<AgentProcessingWorkflowVO>> list(
            @PathVariable Long conversationId) {
        return ApiResponse.success(workflowService.listByConversation(
                currentUserProvider.requireUserId(), conversationId));
    }

    @GetMapping("/processing-workflows/{workflowId}")
    public ApiResponse<AgentProcessingWorkflowVO> get(
            @PathVariable Long workflowId) {
        return ApiResponse.success(workflowService.get(
                currentUserProvider.requireUserId(), workflowId));
    }

    @PostMapping("/processing-workflows/{workflowId}/confirm")
    public ApiResponse<AgentProcessingWorkflowVO> confirm(
            @PathVariable Long workflowId) {
        return ApiResponse.success(workflowService.confirm(
                currentUserProvider.requireUserId(), workflowId));
    }
}
