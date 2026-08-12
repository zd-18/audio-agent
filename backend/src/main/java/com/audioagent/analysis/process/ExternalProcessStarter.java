package com.audioagent.analysis.process;

import java.io.IOException;
import java.util.List;

public interface ExternalProcessStarter {

    Process start(List<String> command) throws IOException;
}
