package com.audioagent.analysis.process;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ExternalProcessContextRegistry {

    private final ThreadLocal<String> currentContext = new ThreadLocal<>();
    private final ConcurrentMap<String, Process> runningProcesses =
            new ConcurrentHashMap<>();
    private final Set<String> cancelledContexts =
            ConcurrentHashMap.newKeySet();

    public void begin(String contextId) {
        currentContext.set(contextId);
    }

    public void register(Process process) {
        String contextId = currentContext.get();
        if (contextId == null) {
            return;
        }
        runningProcesses.put(contextId, process);
        if (cancelledContexts.contains(contextId)) {
            destroyProcessTree(process);
            throw new ExternalProcessCancelledException();
        }
    }

    public void unregister(Process process) {
        String contextId = currentContext.get();
        if (contextId != null) {
            runningProcesses.remove(contextId, process);
        }
    }

    public boolean cancel(String contextId) {
        cancelledContexts.add(contextId);
        Process process = runningProcesses.get(contextId);
        if (process != null) {
            destroyProcessTree(process);
            return true;
        }
        return false;
    }

    public void throwIfCurrentCancelled() {
        String contextId = currentContext.get();
        if (contextId != null && cancelledContexts.contains(contextId)) {
            throw new ExternalProcessCancelledException();
        }
    }

    public void complete(String contextId) {
        runningProcesses.remove(contextId);
        cancelledContexts.remove(contextId);
        if (contextId.equals(currentContext.get())) {
            currentContext.remove();
        }
    }

    private void destroyProcessTree(Process process) {
        process.toHandle().descendants().forEach(handle -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
