package com.audioagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendAgentMessageRequest {

    @NotBlank(message = "content is required")
    private String content;

    @NotBlank(message = "clientRequestId is required")
    @Size(max = 64,
            message = "clientRequestId must not exceed 64 characters")
    private String clientRequestId;

    /**
     * CHAT keeps the existing transcript Q&A behavior. PROCESSING asks the
     * Planner to create a confirmable audio-processing workflow.
     */
    private String mode = "CHAT";
}
