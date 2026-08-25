package com.audioagent.analysis.process;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalProcessExecutorTest {

    @Test
    void streamsLargeStderrAndBoundsDebugOutput() throws Exception {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            output.append("metadata line ").append(i).append('\n');
        }
        ExternalProcessStarter starter = mock(ExternalProcessStarter.class);
        Process process = mock(Process.class);
        ProcessHandle handle = mock(ProcessHandle.class);
        when(starter.start(List.of("ffmpeg"))).thenReturn(process);
        when(process.getInputStream()).thenReturn(
                new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(
                output.toString().getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        when(process.isAlive()).thenReturn(false);
        when(process.toHandle()).thenReturn(handle);
        when(handle.descendants()).thenReturn(Stream.empty());
        AtomicInteger consumed = new AtomicInteger();

        ExternalProcessResult result = new ExternalProcessExecutor(starter,
                new ExternalProcessContextRegistry())
                .execute(List.of("ffmpeg"), 5,
                        (Consumer<String>) line ->
                                consumed.incrementAndGet());

        assertEquals(20_000, consumed.get());
        assertTrue(result.debugOutput().length() <= 16_000);
        assertTrue(result.selectedStderrLines().isEmpty());
    }
}
