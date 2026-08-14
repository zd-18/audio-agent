package com.audioagent.progress.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.common.api.PageResult;
import com.audioagent.progress.service.UserTaskProgressService;
import com.audioagent.progress.vo.UserTaskProgressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-tasks")
@RequiredArgsConstructor
public class UserTaskProgressController {

    private final UserTaskProgressService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResult<UserTaskProgressVO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(
                currentUserProvider.requireUserId(), current, size));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<UserTaskProgressVO> get(
            @PathVariable("taskId") Long taskId) {
        return ApiResponse.success(service.get(
                currentUserProvider.requireUserId(), taskId));
    }
}
