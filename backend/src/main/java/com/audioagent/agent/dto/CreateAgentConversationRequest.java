package com.audioagent.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAgentConversationRequest {

    @NotBlank(message = "transcriptId is required")
    @Pattern(regexp = "[1-9]\\d{0,18}",
            message = "transcriptId is invalid")
    private String transcriptId;

    @Size(max = 120, message = "title must not exceed 120 characters")
    private String title;
}
