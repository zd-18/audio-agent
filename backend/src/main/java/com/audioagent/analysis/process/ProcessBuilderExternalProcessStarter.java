package com.audioagent.analysis.process;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ProcessBuilderExternalProcessStarter
        implements ExternalProcessStarter {

    @Override
    public Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command).start();
    }
}
