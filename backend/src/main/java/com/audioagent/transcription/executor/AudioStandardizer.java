package com.audioagent.transcription.executor;

import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessResult;
import com.audioagent.analysis.process.ExternalProcessTimeoutException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.exception.TranscriptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioStandardizer {

    private final ExternalProcessExecutor processExecutor;
    private final AnalysisProperties analysisProperties;
    private final TranscriptionProperties properties;

    public Path standardize(Path input, Path workDirectory) {
        Path output = workDirectory.resolve("standardized.wav");
        List<String> command = List.of(
                analysisProperties.getFfmpegPath(),
                "-hide_banner", "-nostdin", "-y",
                "-i", input.toString(),
                "-vn", "-ac", "1", "-ar", "16000",
                "-c:a", "pcm_s16le",
                output.toString()
        );
        try {
            ExternalProcessResult result = processExecutor.execute(
                    command, properties.getFfmpegTimeoutSeconds(),
                    line -> false);
            if (result.exitCode() != 0 || !Files.isRegularFile(output)
                    || Files.size(output) <= 44) {
                log.warn("Audio standardization failed, exitCode={}",
                        result.exitCode());
                throw failed(false, "音频无法转换为语音识别格式", null);
            }
            return output;
        } catch (TranscriptionException e) {
            throw e;
        } catch (ExternalProcessTimeoutException e) {
            throw failed(true, "音频标准化超时，请稍后重试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed(true, "音频标准化被中断，请稍后重试", e);
        } catch (Exception e) {
            throw failed(false, "音频标准化失败，请检查文件格式", e);
        }
    }

    private TranscriptionException failed(boolean retryable,
                                          String message,
                                          Throwable cause) {
        return new TranscriptionException(
                ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                retryable, message, cause);
    }
}
