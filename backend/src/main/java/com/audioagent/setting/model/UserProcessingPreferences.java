package com.audioagent.setting.model;

import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;

public record UserProcessingPreferences(
        DenoiseStrength defaultDenoiseStrength,
        ProcessingStrategy processingStrategy,
        boolean autoLimitPeak,
        boolean requireStepConfirmation) {
}
