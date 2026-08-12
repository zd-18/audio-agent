package com.audioagent.processing.pipeline;

import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessResult;
import com.audioagent.analysis.process.ExternalProcessTimeoutException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.audioagent.processing.config.AudioProcessingProperties;
import com.audioagent.processing.exception.ProcessingExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FfmpegCommandExecutorTest {

    @Test
    @SuppressWarnings("unchecked")
    void timeoutIsRetryableAndCommandDoesNotUseAShell() throws Exception {
        ExternalProcessExecutor processExecutor =
                mock(ExternalProcessExecutor.class);
        when(processExecutor.execute(anyList(), anyInt(),
                any(Consumer.class))).thenThrow(
                        new ExternalProcessTimeoutException("timeout"));
        AnalysisProperties analysis = new AnalysisProperties();
        analysis.setFfmpegPath("ffmpeg-custom");
        AudioProcessingProperties processing =
                new AudioProcessingProperties();
        processing.setExecutionTimeoutSeconds(37);
        FfmpegCommandExecutor executor = new FfmpegCommandExecutor(
                analysis, processing, processExecutor);
        Path input = Path.of("folder with spaces", "source.wav");
        Path output = Path.of("output folder", "result.wav");

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.transform(input, output, "volume=3dB",
                        null, null, "NORMALIZE_VOLUME"));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED.name(),
                error.getFailureCode());
        assertTrue(error.isRetryable());
        ArgumentCaptor<List<String>> commandCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(processExecutor).execute(commandCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(37),
                any(Consumer.class));
        List<String> command = commandCaptor.getValue();
        assertEquals("ffmpeg-custom", command.getFirst());
        assertTrue(command.contains(input.toAbsolutePath().toString()));
        assertTrue(command.contains(output.toAbsolutePath().toString()));
        assertFalse(command.contains("cmd.exe"));
        assertFalse(command.contains("/c"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonZeroExitCodeIsPermanentAndRetainsDiagnosticCode()
            throws Exception {
        ExternalProcessExecutor processExecutor =
                mock(ExternalProcessExecutor.class);
        when(processExecutor.execute(anyList(), anyInt(),
                any(Consumer.class))).thenReturn(new ExternalProcessResult(
                17, List.of(), "Invalid data found when processing input"));
        FfmpegCommandExecutor executor = new FfmpegCommandExecutor(
                new AnalysisProperties(), new AudioProcessingProperties(),
                processExecutor);

        ProcessingExecutionException error = assertThrows(
                ProcessingExecutionException.class,
                () -> executor.transform(Path.of("source.wav"),
                        Path.of("result.wav"), "anull", null, null,
                        "TRIM_SEGMENT"));

        assertEquals(ErrorCode.PROCESSING_EXECUTION_FFMPEG_FAILED.name(),
                error.getFailureCode());
        assertFalse(error.isRetryable());
        assertTrue(error.getMessage().contains("code 17"));
        assertTrue(error.getMessage().contains("TRIM_SEGMENT"));
    }
}
