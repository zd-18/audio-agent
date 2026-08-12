package com.audioagent.analysis.process;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runs an external process while concurrently consuming both output streams.
 */
@Component
@RequiredArgsConstructor
public class ExternalProcessExecutor {

    private static final int DEBUG_OUTPUT_LIMIT = 16_000;
    private static final int COLLECTOR_WAIT_SECONDS = 5;

    private final ExternalProcessStarter processStarter;

    public ExternalProcessResult execute(
            List<String> command,
            int timeoutSeconds,
            Predicate<String> stderrSelector
    ) throws IOException, InterruptedException,
            ExternalProcessTimeoutException, ExecutionException,
            TimeoutException {
        List<String> selectedLines = new ArrayList<>();
        ExternalProcessResult result = execute(command, timeoutSeconds,
                (Consumer<String>) line -> {
                    if (stderrSelector.test(line)) {
                        selectedLines.add(line);
                    }
                });
        return new ExternalProcessResult(result.exitCode(),
                List.copyOf(selectedLines), result.debugOutput());
    }

    /**
     * Streams stderr lines to a consumer without retaining raw output.
     */
    public ExternalProcessResult execute(
            List<String> command,
            int timeoutSeconds,
            Consumer<String> stderrConsumer
    ) throws IOException, InterruptedException,
            ExternalProcessTimeoutException, ExecutionException,
            TimeoutException {
        Process process = processStarter.start(command);
        try (ExecutorService streamExecutor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> stdoutFuture = streamExecutor.submit(
                    () -> drain(process.getInputStream()));
            Future<CollectedStderr> stderrFuture = streamExecutor.submit(
                    () -> collectStderr(process.getErrorStream(),
                            stderrConsumer));

            boolean finished = process.waitFor(timeoutSeconds,
                    TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                cancelIfIncomplete(stdoutFuture);
                cancelIfIncomplete(stderrFuture);
                throw new ExternalProcessTimeoutException(
                        "External process timed out");
            }

            CollectedStderr stderr = stderrFuture.get(
                    COLLECTOR_WAIT_SECONDS, TimeUnit.SECONDS);
            stdoutFuture.get(COLLECTOR_WAIT_SECONDS, TimeUnit.SECONDS);
            return new ExternalProcessResult(process.exitValue(),
                    List.of(),
                    stderr.debugOutput());
        } finally {
            if (process.isAlive()) {
                destroyProcessTree(process);
            }
        }
    }

    private CollectedStderr collectStderr(
            InputStream stream,
            Consumer<String> consumer
    ) {
        StringBuilder debugOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(line);
                appendLimited(debugOutput, line);
            }
            return new CollectedStderr(debugOutput.toString());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read external process stderr", e);
        }
    }

    private void drain(InputStream stream) {
        try (InputStream input = stream) {
            input.transferTo(OutputStreamSink.INSTANCE);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to drain external process stdout", e);
        }
    }

    private void appendLimited(StringBuilder target, String line) {
        if (target.length() >= DEBUG_OUTPUT_LIMIT) {
            return;
        }
        String value = target.isEmpty()
                ? line : System.lineSeparator() + line;
        int remaining = DEBUG_OUTPUT_LIMIT - target.length();
        target.append(value, 0, Math.min(value.length(), remaining));
    }

    private void cancelIfIncomplete(Future<?> future) {
        if (!future.isDone()) {
            future.cancel(true);
        }
    }

    private void destroyProcessTree(Process process) {
        process.toHandle().descendants().forEach(handle -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        process.destroyForcibly();
    }

    private record CollectedStderr(String debugOutput) {
    }

    private static final class OutputStreamSink
            extends java.io.OutputStream {

        private static final OutputStreamSink INSTANCE =
                new OutputStreamSink();

        @Override
        public void write(int ignored) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
        }
    }
}
