package com.audioagent.analysis.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.service.AudioResourceOwnershipService;
import com.audioagent.analysis.dto.CreateTaskRequest;
import com.audioagent.analysis.dto.UpdateProcessingStepConfirmationRequest;
import com.audioagent.analysis.service.AudioAnalysisTaskService;
import com.audioagent.analysis.service.AudioIssueSegmentService;
import com.audioagent.analysis.service.AudioAnalysisReportService;
import com.audioagent.analysis.service.AudioProcessingPlanService;
import com.audioagent.analysis.service.AudioProcessingConfirmationService;
import com.audioagent.analysis.vo.AudioAnalysisReportVO;
import com.audioagent.analysis.vo.IssueSummaryVO;
import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.common.api.PageResult;
import com.audioagent.file.service.AudioVersionService;
import com.audioagent.file.vo.AudioVersionChainVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audio-analysis")
@RequiredArgsConstructor
public class AudioAnalysisTaskController {

    private final AudioAnalysisTaskService audioAnalysisTaskService;
    private final AudioIssueSegmentService audioIssueSegmentService;
    private final AudioAnalysisReportService audioAnalysisReportService;
    private final AudioProcessingPlanService audioProcessingPlanService;
    private final AudioProcessingConfirmationService
            audioProcessingConfirmationService;
    private final CurrentUserProvider currentUserProvider;
    private final AudioResourceOwnershipService ownershipService;
    private final AudioVersionService audioVersionService;

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskListVO>> listTasks(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long audioFileId,
            @RequestParam(required = false) String analysisType,
            @RequestParam(required = false) String keyword
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioAnalysisTaskService.listTasks(
                userId, current, size, status, audioFileId, analysisType,
                keyword));
    }

    @PostMapping("/tasks")
    public ApiResponse<TaskVO> createTask(
            @Valid @RequestBody CreateTaskRequest request
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireFileOwned(userId, request.getAudioFileId());
        TaskVO result = audioAnalysisTaskService.createTask(request);
        return ApiResponse.success(result);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskVO> getTaskDetail(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        TaskVO result = audioAnalysisTaskService.getTaskDetail(taskId);
        return ApiResponse.success(result);
    }

    @GetMapping("/tasks/{taskId}/audio-versions")
    public ApiResponse<AudioVersionChainVO> getAudioVersions(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                audioVersionService.getByTask(userId, taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<TaskVO> retryTask(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        TaskVO result = audioAnalysisTaskService.retryTask(taskId);
        return ApiResponse.success(result);
    }

    @GetMapping("/tasks/{taskId}/issues")
    public ApiResponse<IssueSummaryVO> getIssues(
            @PathVariable("taskId") Long taskId,
            @RequestParam(required = false) String issueType
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        return ApiResponse.success(
                audioIssueSegmentService.getIssues(taskId, issueType));
    }

    @GetMapping("/tasks/{taskId}/report")
    public ApiResponse<AudioAnalysisReportVO> getReport(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        return ApiResponse.success(audioAnalysisReportService
                .getReport(taskId));
    }

    @PostMapping("/tasks/{taskId}/processing-plan")
    public ApiResponse<ProcessingPlanVO> generateProcessingPlan(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        return ApiResponse.success(audioProcessingPlanService
                .generateForOwner(userId, taskId));
    }

    @GetMapping("/tasks/{taskId}/processing-plan")
    public ApiResponse<ProcessingPlanVO> getProcessingPlan(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        ownershipService.requireTaskOwned(userId, taskId);
        return ApiResponse.success(audioProcessingPlanService.get(taskId));
    }

    @PostMapping("/tasks/{taskId}/processing-confirmation")
    public ApiResponse<ProcessingConfirmationVO> createProcessingConfirmation(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioProcessingConfirmationService
                .create(userId, taskId));
    }

    @GetMapping("/tasks/{taskId}/processing-confirmation")
    public ApiResponse<ProcessingConfirmationVO> getProcessingConfirmation(
            @PathVariable("taskId") Long taskId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioProcessingConfirmationService
                .getCurrent(userId, taskId));
    }

    @PutMapping("/processing-confirmations/{confirmationId}/steps/{stepConfirmationId}")
    public ApiResponse<ProcessingConfirmationVO.Step>
    updateProcessingStepConfirmation(
            @PathVariable("confirmationId") Long confirmationId,
            @PathVariable("stepConfirmationId") Long stepConfirmationId,
            @Valid @RequestBody UpdateProcessingStepConfirmationRequest request
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioProcessingConfirmationService
                .updateStep(userId, confirmationId, stepConfirmationId,
                        request));
    }

    @PostMapping("/processing-confirmations/{confirmationId}/confirm")
    public ApiResponse<ProcessingConfirmationVO> confirmProcessingConfirmation(
            @PathVariable("confirmationId") Long confirmationId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioProcessingConfirmationService
                .confirm(userId, confirmationId));
    }

    @PostMapping("/processing-confirmations/{confirmationId}/cancel")
    public ApiResponse<ProcessingConfirmationVO> cancelProcessingConfirmation(
            @PathVariable("confirmationId") Long confirmationId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioProcessingConfirmationService
                .cancel(userId, confirmationId));
    }
}
