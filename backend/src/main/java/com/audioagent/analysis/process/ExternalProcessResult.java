package com.audioagent.analysis.process;

import java.util.List;

public record ExternalProcessResult(
        int exitCode,
        List<String> selectedStderrLines,
        String debugOutput
) {
}
