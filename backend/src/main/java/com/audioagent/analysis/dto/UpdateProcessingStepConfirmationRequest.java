package com.audioagent.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class UpdateProcessingStepConfirmationRequest {

    @NotBlank
    private String decision;

    @NotNull
    private Boolean userConfirmed;

    private Map<String, Object> parameterOverrides = new LinkedHashMap<>();

    @Size(max = 500)
    private String userNote;
}

