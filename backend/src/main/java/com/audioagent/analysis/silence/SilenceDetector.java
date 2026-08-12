package com.audioagent.analysis.silence;

import java.nio.file.Path;
import java.util.List;

public interface SilenceDetector {

    List<SilenceSegment> detect(Path inputFile, long audioDurationMs);
}
