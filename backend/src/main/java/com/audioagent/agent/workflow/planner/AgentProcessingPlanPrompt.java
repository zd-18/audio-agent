package com.audioagent.agent.workflow.planner;

import com.audioagent.agent.workflow.model.AgentProcessingContext;
import org.springframework.stereotype.Component;

@Component
public class AgentProcessingPlanPrompt {

    public static final String VERSION = "audio-processing-planner-v1";

    public String systemPrompt() {
        return """
                You are an audio processing Planner. Produce a plan only; never
                execute anything. The only supported operationType values are
                NORMALIZE_VOLUME, TRIM_SEGMENT and DENOISE.

                Never output FFmpeg commands, shell commands, command-line
                arguments, filter graphs, scripts, URLs, or arbitrary tool names.
                Return exactly one JSON object with only these fields:
                {
                  "summary": "short user-facing Chinese summary",
                  "steps": [
                    {
                      "order": 1,
                      "operationType": "NORMALIZE_VOLUME",
                      "parameters": {
                        "targetLufs": -16,
                        "truePeakLimitDbfs": -1
                      },
                      "startMs": null,
                      "endMs": null,
                      "reason": "short user-facing Chinese reason"
                    }
                  ]
                }

                NORMALIZE_VOLUME is a whole-audio operation. Its only parameters
                are targetLufs (-24 through -8) and truePeakLimitDbfs (-6 through
                0). DENOISE is a whole-audio operation that reduces continuous
                background noise; its only parameter is strength, one of LIGHT,
                MEDIUM or STRONG, chosen by how much noise the user mentions
                (for example "背景噪声大", "去噪", "降低底噪" suggest STRONG or
                MEDIUM; a subtle hiss suggests LIGHT). TRIM_SEGMENT removes the
                interval [startMs, endMs) and must have an empty parameters
                object. All times are integer milliseconds within the supplied
                audio duration. Steps must be ordered continuously from 1. Do
                not invent another operation.
                """;
    }

    public String userPrompt(AgentProcessingContext context,
                             String requirement) {
        return """
                [AUDIO_CONTEXT]
                audioFileId=%s
                fileName=%s
                durationMs=%s
                sampleRate=%s
                channels=%s
                transcriptSegments:
                %s
                [/AUDIO_CONTEXT]

                [USER_REQUIREMENT]
                %s
                [/USER_REQUIREMENT]
                """.formatted(context.audioFileId(),
                safe(context.audioFileName()), context.durationMs(),
                context.sampleRate(), context.channels(),
                safe(context.transcriptContext()), requirement);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
