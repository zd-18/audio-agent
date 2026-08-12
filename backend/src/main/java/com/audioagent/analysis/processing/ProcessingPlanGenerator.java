package com.audioagent.analysis.processing;

/**
 * Generates a user-facing processing proposal from persisted analysis data.
 * Implementations must not modify or re-analyze the audio file.
 */
public interface ProcessingPlanGenerator {

    ProcessingPlanDraft generate(ProcessingPlanContext context);
}
