package com.audioagent.processing.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.common.api.PageResult;
import com.audioagent.processing.dto.CreateProcessingExecutionRequest;
import com.audioagent.processing.service.AudioProcessingExecutionService;
import com.audioagent.processing.vo.ProcessingExecutionVO;
import com.audioagent.processing.vo.ProcessingExecutionListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AudioProcessingExecutionController {

    private final AudioProcessingExecutionService executionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/api/audio-processing/executions")
    public ApiResponse<PageResult<ProcessingExecutionListVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(executionService.list(userId, current,
                size, status));
    }

    @PostMapping("/api/audio-processing/executions")
    public ApiResponse<ProcessingExecutionVO> create(
            @Valid @RequestBody CreateProcessingExecutionRequest request) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(executionService.create(userId,
                request.getConfirmationId()));
    }

    @GetMapping("/api/audio-processing/executions/{executionId}")
    public ApiResponse<ProcessingExecutionVO> get(
            @PathVariable("executionId") Long executionId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(executionService.get(userId,
                executionId));
    }

    @PostMapping("/api/audio-processing/executions/{executionId}/retry")
    public ApiResponse<ProcessingExecutionVO> retry(
            @PathVariable("executionId") Long executionId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(executionService.retry(userId,
                executionId));
    }

    @GetMapping("/api/audio-analysis/tasks/{taskId}/processing-execution")
    public ApiResponse<ProcessingExecutionVO> getByTask(
            @PathVariable("taskId") Long taskId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(executionService.getByTask(userId,
                taskId));
    }
}
