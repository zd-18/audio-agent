package com.audioagent.infrastructure.ffprobe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FfprobeAudioMetadataService implements AudioMetadataService {

    private final FfprobeProperties ffprobeProperties;

    @Override
    public AudioMetadata extractMetadata(Path tempFile) {
        Long durationMs = null;
        try {
            durationMs = probeDuration(tempFile);
        } catch (Exception e) {
            log.warn(
                    "FFprobe duration extraction failed, file={}, reason={}",
                    tempFile.getFileName(),
                    e.getMessage()
            );
        }
        return AudioMetadata.builder().durationMs(durationMs).build();
    }

    private Long probeDuration(Path tempFile) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                ffprobeProperties.getExecutable(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                tempFile.toAbsolutePath().toString()
        );

        Process process = processBuilder.start();
        try {
            boolean finished = process.waitFor(
                    ffprobeProperties.getTimeoutSeconds(),
                    TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFprobe process timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String stderr;
                try (InputStream errorStream = process.getErrorStream()) {
                    stderr = new String(
                            errorStream.readAllBytes(),
                            StandardCharsets.UTF_8
                    ).trim();
                }
                throw new RuntimeException(
                        "FFprobe exited with code " + exitCode
                                + (stderr.isEmpty() ? "" : ": " + stderr)
                );
            }

            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                ).trim();
            }

            if (output.isEmpty()) {
                throw new RuntimeException("FFprobe produced empty output");
            }

            BigDecimal durationSeconds = new BigDecimal(output);
            long durationMs = durationSeconds
                    .multiply(BigDecimal.valueOf(1000))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            if (durationMs <= 0) {
                throw new RuntimeException(
                        "Invalid duration: " + durationMs + "ms"
                );
            }

            log.debug(
                    "FFprobe duration extracted: {}ms, file={}",
                    durationMs,
                    tempFile.getFileName()
            );

            return durationMs;

        } finally {
            process.destroyForcibly();
        }
    }
}
