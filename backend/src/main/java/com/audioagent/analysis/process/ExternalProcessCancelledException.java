package com.audioagent.analysis.process;

public class ExternalProcessCancelledException extends RuntimeException {

    public ExternalProcessCancelledException() {
        super("External process was cancelled");
    }
}
