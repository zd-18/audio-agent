package com.audioagent.transcription.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.exception.TranscriptionException;
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
public class TranscriptionWorkDirectory {

    private final TranscriptionProperties properties;

    public Path prepare(Long taskId) {
        Path directory = resolve(taskId);
        clean(taskId);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw failure("无法创建音频转写临时目录", e);
        }
    }

    public void clean(Long taskId) {
        Path directory = resolve(taskId);
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
            throw failure("无法清理音频转写临时文件", e.getCause());
        } catch (IOException e) {
            throw failure("无法检查音频转写临时目录", e);
        }
    }

    public void cleanQuietly(Long taskId) {
        try {
            clean(taskId);
        } catch (RuntimeException e) {
            log.error("Transcription temporary files cleanup failed, taskId={}",
                    taskId, e);
        }
    }

    private Path resolve(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw failure("转写任务标识无效", null);
        }
        Path root = Path.of(properties.getTempRoot())
                .toAbsolutePath().normalize();
        Path directory = root.resolve(taskId.toString()).normalize();
        if (directory.equals(root) || !directory.startsWith(root)) {
            throw failure("音频转写临时目录不安全", null);
        }
        return directory;
    }

    private TranscriptionException failure(String message,
                                           Throwable cause) {
        return new TranscriptionException(
                ErrorCode.AUDIO_STANDARDIZATION_FAILED, true, message,
                cause);
    }

    private static final class CleanupException extends RuntimeException {
        private CleanupException(IOException cause) {
            super(cause);
        }
    }
}
