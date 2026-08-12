package com.audioagent.processing.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.exception.ProcessingExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingExecutionWorkDirectory {

    private final AudioProcessingProperties properties;

    public Path prepare(Long executionId) {
        Path directory = resolve(executionId);
        clean(executionId);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw temporary("Execution work directory cannot be created", e);
        }
    }

    public void clean(Long executionId) {
        Path directory = resolve(executionId);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new CleanupException(e);
                }
            });
        } catch (CleanupException e) {
            throw temporary("Execution work directory cannot be cleaned",
                    e.getCause());
        } catch (IOException e) {
            throw temporary("Execution work directory cannot be inspected",
                    e);
        }
    }

    public void cleanQuietly(Long executionId) {
        try {
            clean(executionId);
        } catch (RuntimeException e) {
            log.warn("Failed to clean processing work directory, executionId={}",
                    executionId);
        }
    }

    private Path resolve(Long executionId) {
        if (executionId == null || executionId <= 0) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false, "executionId is invalid");
        }
        Path root = Path.of(properties.getTempRoot())
                .toAbsolutePath().normalize();
        Path directory = root.resolve(executionId.toString()).normalize();
        if (!directory.startsWith(root)
                || !directory.getFileName().toString()
                .equals(executionId.toString())) {
            throw new ProcessingExecutionException(
                    ErrorCode.PROCESSING_EXECUTION_INVALID_PARAMETER,
                    false, "Execution work directory is unsafe");
        }
        return directory;
    }

    private ProcessingExecutionException temporary(String message,
                                                    Throwable cause) {
        return new ProcessingExecutionException(
                ErrorCode.PROCESSING_EXECUTION_FAILED,
                true, message, cause);
    }

    private static final class CleanupException extends RuntimeException {
        private CleanupException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
