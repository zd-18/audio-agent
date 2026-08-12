package com.audioagent.processing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateProcessingExecutionRequest {

    @NotNull
    @Positive
    private Long confirmationId;
}
