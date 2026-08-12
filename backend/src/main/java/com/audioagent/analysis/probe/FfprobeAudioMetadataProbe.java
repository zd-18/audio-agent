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
                "stream=codec_name,sample_rate,channels,bit_rate"
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

            return parseOutput(stdout);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ffprobe 执行异常: " + e.getMessage());
        } finally {
            process.destroyForcibly();
        }
    }

    private AudioMetadata parseOutput(String json) {
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

            String formatName = format != null && format.has("format_name")
                    ? format.get("format_name").asText() : null;
            Long formatBitRate = format != null
                    ? parseLongSafe(format, "bit_rate") : null;
            Long fileSize = format != null
                    ? parseLongSafe(format, "size") : null;

            Long durationMs = null;
            if (format != null && format.has("duration")) {
                BigDecimal duration = new BigDecimal(
                        format.get("duration").asText()
                );
                durationMs = duration
                        .multiply(BigDecimal.valueOf(1000))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
            }

            Long bitRate = streamBitRate != null
                    ? streamBitRate : formatBitRate;

            return AudioMetadata.builder()
                    .formatName(formatName)
                    .codecName(codecName)
                    .durationMs(durationMs)
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
}
