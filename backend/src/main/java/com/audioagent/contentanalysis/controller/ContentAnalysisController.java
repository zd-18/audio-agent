package com.audioagent.contentanalysis.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.contentanalysis.dto.CreateContentAnalysisTaskRequest;
import com.audioagent.contentanalysis.service.ContentAnalysisService;
import com.audioagent.contentanalysis.vo.ContentAnalysisResultVO;
import com.audioagent.contentanalysis.vo.ContentAnalysisTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content-analysis/tasks")
@RequiredArgsConstructor
public class ContentAnalysisController {

    private final ContentAnalysisService service;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<ContentAnalysisTaskVO> create(
            @Valid @RequestBody
            CreateContentAnalysisTaskRequest request) {
        return ApiResponse.success(service.create(
                currentUserProvider.requireUserId(), request));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ContentAnalysisTaskVO> get(
            @PathVariable String taskId) {
        return ApiResponse.success(service.get(
                currentUserProvider.requireUserId(), taskId));
    }

    @GetMapping("/{taskId}/result")
    public ApiResponse<ContentAnalysisResultVO> getResult(
            @PathVariable String taskId) {
        return ApiResponse.success(service.getResult(
                currentUserProvider.requireUserId(), taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<ContentAnalysisTaskVO> retry(
            @PathVariable String taskId) {
        return ApiResponse.success(service.retry(
                currentUserProvider.requireUserId(), taskId));
    }
}
