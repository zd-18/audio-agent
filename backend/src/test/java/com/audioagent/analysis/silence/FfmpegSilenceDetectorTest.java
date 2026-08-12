package com.audioagent.analysis.silence;

import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessStarter;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FfmpegSilenceDetectorTest {

    @TempDir
    Path tempDir;

    private AnalysisProperties properties;
    private ExternalProcessStarter processStarter;
    private FfmpegSilenceDetector detector;
    private Path audioFile;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AnalysisProperties();
        properties.setFfmpegPath("D:/Program Files/ffmpeg/ffmpeg.exe");
        processStarter = mock(ExternalProcessStarter.class);
        detector = new FfmpegSilenceDetector(properties,
                new SilenceDetectOutputParser(),
                new ExternalProcessExecutor(processStarter));
        audioFile = Files.createFile(tempDir.resolve("audio input.wav"));
    }

    @Test
    void buildsArgumentListWithoutShellQuoting() {
        List<String> command = detector.buildCommand(audioFile,
                properties.getSilence());

        assertEquals("D:/Program Files/ffmpeg/ffmpeg.exe", command.get(0));
        assertEquals(audioFile.toAbsolutePath().toString(), command.get(4));
        assertEquals("silencedetect=n=-45dB:d=1.5", command.get(6));
        assertFalse(command.get(4).startsWith("\""));
    }

    @Test
    void classifiesMissingFfmpegAsNonRetryable() throws Exception {
        when(processStarter.start(anyList()))
                .thenThrow(new IOException("CreateProcess error=2"));

        AudioAnalysisException exception = assertThrows(
                AudioAnalysisException.class,
                () -> detector.detect(audioFile, 10_000));

        assertEquals(AudioAnalysisException.ErrorCodes.FFMPEG_NOT_FOUND,
                exception.getErrorCode());
        assertFalse(exception.isRetryable());
    }

    @Test
    void classifiesTimeoutAsRetryableAndDestroysProcess() throws Exception {
        Process process = mockProcess(false, "");
        when(processStarter.start(anyList())).thenReturn(process);

        AudioAnalysisException exception = assertThrows(
                AudioAnalysisException.class,
                () -> detector.detect(audioFile, 10_000));

        assertEquals(
                AudioAnalysisException.ErrorCodes.SILENCE_DETECT_TIMEOUT,
                exception.getErrorCode());
        assertTrue(exception.isRetryable());
    }

    @Test
    void detectsNoSilenceAsSuccessfulEmptyResult() throws Exception {
        Process process = mockProcess(true, "ffmpeg metadata only");
        when(process.exitValue()).thenReturn(0);
        when(processStarter.start(anyList())).thenReturn(process);

        assertTrue(detector.detect(audioFile, 10_000).isEmpty());
    }

    private Process mockProcess(boolean finished, String stderr)
            throws InterruptedException {
        Process process = mock(Process.class);
        ProcessHandle handle = mock(ProcessHandle.class);
        when(process.getInputStream()).thenReturn(
                new ByteArrayInputStream(new byte[0]));
        when(process.getErrorStream()).thenReturn(
                new ByteArrayInputStream(
                        stderr.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)))
                .thenReturn(finished);
        when(process.toHandle()).thenReturn(handle);
        when(handle.descendants()).thenReturn(Stream.empty());
        when(process.destroyForcibly()).thenReturn(process);
        when(process.isAlive()).thenReturn(false);
        return process;
    }
}
