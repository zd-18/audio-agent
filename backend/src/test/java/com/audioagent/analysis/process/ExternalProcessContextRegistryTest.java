package com.audioagent.analysis.process;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalProcessContextRegistryTest {

    @Test
    void cancellationDestroysRegisteredProcessTreeAndMarksContext() {
        ExternalProcessContextRegistry registry =
                new ExternalProcessContextRegistry();
        Process process = mock(Process.class);
        ProcessHandle root = mock(ProcessHandle.class);
        ProcessHandle child = mock(ProcessHandle.class);
        when(process.toHandle()).thenReturn(root);
        when(root.descendants()).thenReturn(Stream.of(child));
        when(child.isAlive()).thenReturn(true);
        when(process.isAlive()).thenReturn(true);

        registry.begin("audio-processing:90");
        registry.register(process);

        assertTrue(registry.cancel("audio-processing:90"));
        verify(child).destroyForcibly();
        verify(process).destroyForcibly();
        assertThrows(ExternalProcessCancelledException.class,
                registry::throwIfCurrentCancelled);
        registry.complete("audio-processing:90");
    }
}
