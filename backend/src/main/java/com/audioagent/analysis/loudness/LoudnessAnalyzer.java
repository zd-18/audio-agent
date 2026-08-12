package com.audioagent.analysis.loudness;

import java.nio.file.Path;
import java.util.Optional;

public interface LoudnessAnalyzer {

    Optional<LoudnessAnalysis> analyze(Path inputFile);
}
