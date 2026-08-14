package com.audioagent.analysis.probe;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FfprobeAudioMetadataProbe implements AudioMetadataProbe {

    private final AnalysisProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public AudioMetadata probe(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new RuntimeException("音频文件不存在: " + filePath.getFileName());
        }

        List<String> command = List.of(
                properties.getFfprobePath(),
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries",
                "stream=codec_name,duration,sample_rate,channels,bit_rate"
                        + ":format=duration,format_name,bit_rate,size",
                "-of", "json",
                filePath.toAbsolutePath().toString()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process;
        try {
            process = processBuilder.start();
        } catch (Exception e) {
            throw new RuntimeException("ffprobe 程序不存在或无法启动: " + properties.getFfprobePath());
        }

        try {
            boolean finished = process.waitFor(
                    properties.getTimeoutSeconds(),
                    TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("ffprobe 执行超时");
            }

            int exitCode = process.exitValue();

            String stderr;
            try (InputStream errorStream = process.getErrorStream()) {
                stderr = new String(
                        errorStream.readAllBytes(),
                        StandardCharsets.UTF_8
                ).trim();
            }

            if (exitCode != 0) {
                String shortError = stderr.length() > 200
                        ? stderr.substring(0, 200)
                        : stderr;
                throw new RuntimeException(
                        "ffprobe 执行失败: " + shortError
                );
            }

            String stdout;
            try (InputStream inputStream = process.getInputStream()) {
                stdout = new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            AudioMetadata metadata = parseOutput(stdout);
            logDiagnostics(filePath, metadata);
            return metadata;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ffprobe 执行异常: " + e.getMessage());
        } finally {
            process.destroyForcibly();
        }
    }

    AudioMetadata parseOutput(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode streams = root.get("streams");
            if (streams == null || streams.isEmpty()) {
                throw new RuntimeException("未检测到音频流");
            }

            JsonNode audioStream = streams.get(0);
            JsonNode format = root.get("format");

            String codecName = audioStream.has("codec_name")
                    ? audioStream.get("codec_name").asText() : null;
            Integer sampleRate = parseIntSafe(audioStream, "sample_rate");
            Integer channels = audioStream.has("channels")
                    ? audioStream.get("channels").asInt() : null;
            Long streamBitRate = parseLongSafe(audioStream, "bit_rate");
            Long streamDurationMs = parseDurationSafe(
                    audioStream, "duration");

            String formatName = format != null && format.has("format_name")
                    ? format.get("format_name").asText() : null;
            Long formatBitRate = format != null
                    ? parseLongSafe(format, "bit_rate") : null;
            Long fileSize = format != null
                    ? parseLongSafe(format, "size") : null;

            Long formatDurationMs = parseDurationSafe(format, "duration");
            Long durationMs = isPositive(streamDurationMs)
                    ? streamDurationMs : formatDurationMs;

            Long bitRate = streamBitRate != null
                    ? streamBitRate : formatBitRate;

            return AudioMetadata.builder()
                    .formatName(formatName)
                    .codecName(codecName)
                    .durationMs(durationMs)
                    .formatDurationMs(formatDurationMs)
                    .streamDurationMs(streamDurationMs)
                    .sampleRate(sampleRate)
                    .channels(channels)
                    .bitRate(bitRate)
                    .fileSize(fileSize)
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "ffprobe 返回结果解析失败: " + e.getMessage()
            );
        }
    }

    private Integer parseIntSafe(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        try {
            return Integer.parseInt(node.get(field).asText());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse {} as int: {}", field, node.get(field).asText());
            return null;
        }
    }

    private Long parseLongSafe(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        try {
            return Long.parseLong(node.get(field).asText());
        } catch (NumberFormatException e) {
            log.debug("Failed to parse {} as long: {}", field, node.get(field).asText());
            return null;
        }
    }

    private Long parseDuration(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        BigDecimal duration = new BigDecimal(node.get(field).asText());
        return duration.multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private Long parseDurationSafe(JsonNode node, String field) {
        try {
            return parseDuration(node, field);
        } catch (RuntimeException e) {
            log.debug("Failed to parse {} as duration: {}", field,
                    node.get(field).asText());
            return null;
        }
    }

    private boolean isPositive(Long durationMs) {
        return durationMs != null && durationMs > 0;
    }

    private void logDiagnostics(Path filePath, AudioMetadata metadata) {
        String message = "Audio metadata diagnostics, fileName={}, "
                + "extension={}, formatDurationMs={}, audioStreamDurationMs={}, "
                + "durationMsUsedByApplication={}, formatName={}, "
                + "codecName={}, sampleRate={}, channels={}";
        Object[] values = {
                safeFileName(filePath), extension(filePath),
                metadata.getFormatDurationMs(),
                metadata.getStreamDurationMs(), metadata.getDurationMs(),
                metadata.getFormatName(), metadata.getCodecName(),
                metadata.getSampleRate(), metadata.getChannels()
        };
        if (isPositive(metadata.getFormatDurationMs())
                && isPositive(metadata.getStreamDurationMs())
                && !metadata.getFormatDurationMs().equals(
                metadata.getStreamDurationMs())) {
            log.info(message, values);
        } else {
            log.debug(message, values);
        }
    }

    private String safeFileName(Path filePath) {
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return "unknown";
        }
        String safe = fileName.toString().replaceAll("[\\r\\n\\t]", "_");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private String extension(Path filePath) {
        String fileName = safeFileName(filePath);
        int separator = fileName.lastIndexOf('.');
        return separator < 0 || separator == fileName.length() - 1
                ? null : fileName.substring(separator + 1);
    }
}
