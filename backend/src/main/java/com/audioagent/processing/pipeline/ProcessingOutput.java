package com.audioagent.processing.pipeline;

import java.nio.file.Path;

public record ProcessingOutput(Path path, long expectedDurationMs) {
}
