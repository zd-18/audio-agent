package com.audioagent.outbox.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.outbox.entity.OutboxEvent;
import com.audioagent.outbox.service.OutboxEventService;
import com.audioagent.outbox.vo.OutboxRetryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/outbox/events")
@RequiredArgsConstructor
public class OutboxEventController {

    private final OutboxEventService outboxEventService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Internal operations endpoint. The project has no administrator role
     * model yet; restrict this endpoint to administrators/operators when that
     * authorization model is introduced.
     */
    @PostMapping("/{eventId}/retry")
    public ApiResponse<OutboxRetryResult> retryFailed(
            @PathVariable("eventId") Long eventId) {
        Long userId = currentUserProvider.requireUserId();
        OutboxEvent retried = outboxEventService.retryFailed(eventId);
        log.warn("User requested outbox event retry, userId={}, eventId={}",
                userId, eventId);
        return ApiResponse.success(OutboxRetryResult.from(retried));
    }
}
