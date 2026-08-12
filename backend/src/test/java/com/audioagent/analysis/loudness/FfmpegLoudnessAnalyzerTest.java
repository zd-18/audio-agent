package com.audioagent.analysis.loudness;

import com.audioagent.analysis.exception.AudioAnalysisException;
import com.audioagent.analysis.process.ExternalProcessExecutor;
import com.audioagent.analysis.process.ExternalProcessResult;
import com.audioagent.analysis.process.ExternalProcessTimeoutException;
import com.audioagent.analysis.noise.NoiseFrameOutputParser;
import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FfmpegLoudnessAnalyzerTest {

    @TempDir
    Path tempDir;

    private AnalysisProperties properties;
    private ExternalProcessExecutor processExecutor;
    private FfmpegLoudnessAnalyzer analyzer;
    private Path audioFile;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AnalysisProperties();
        properties.setFfmpegPath("D:/Program Files/ffmpeg/ffmpeg.exe");
        processExecutor = mock(ExternalProcessExecutor.class);
        analyzer = new FfmpegLoudnessAnalyzer(properties,
                new Ebur128OutputParser(), new NoiseFrameOutputParser(),
                processExecutor);
        audioFile = Files.createFile(tempDir.resolve("audio input.wav"));
    }

    @Test
    void buildsArgumentListWithBothPeakModesAndNoShellQuotes() {
        List<String> command = analyzer.buildCommand(audioFile);

        assertEquals("D:/Program Files/ffmpeg/ffmpeg.exe", command.get(0));
        assertEquals(audioFile.toAbsolutePath().toString(), command.get(4));
        assertTrue(command.get(6).startsWith(
                "ebur128=metadata=1:peak=sample+true,aresample=48000,"));
        assertTrue(command.get(6).contains("asetnsamples=n=4800:p=1"));
        assertTrue(command.get(6).contains("astats="));
        assertTrue(command.get(6).contains("aspectralstats="));
        assertTrue(command.get(6).endsWith("ametadata=mode=print"));
        assertFalse(command.get(4).startsWith("\""));
    }

    @Test
    void volumeSegmentDisabledOmitsFrameMetadataFilter() {
        properties.getVolumeSegment().setEnabled(false);
        properties.getNoiseRisk().setEnabled(false);

        List<String> command = analyzer.buildCommand(audioFile);

        assertEquals("ebur128=peak=sample+true", command.get(6));
    }

    @Test
    void disabledConfigurationDoesNotStartProcess() throws Exception {
        properties.getLoudness().setEnabled(false);
        properties.getVolumeSegment().setEnabled(false);
        properties.getNoiseRisk().setEnabled(false);

        assertFalse(analyzer.analyze(audioFile).isPresent());

        verify(processExecutor, never()).execute(anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<Consumer<String>>any());
    }

    @Test
    void timeoutUsesRetryableLoudnessErrorCode() throws Exception {
        when(processExecutor.execute(anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<Consumer<String>>any()))
                .thenThrow(new ExternalProcessTimeoutException("timeout"));

        AudioAnalysisException exception = assertThrows(
                AudioAnalysisException.class,
                () -> analyzer.analyze(audioFile));

        assertEquals(AudioAnalysisException.ErrorCodes
                        .LOUDNESS_ANALYSIS_TIMEOUT,
                exception.getErrorCode());
        assertEquals(true, exception.isRetryable());
    }

    @Test
    void missingSummaryUsesNonRetryableParseError() throws Exception {
        when(processExecutor.execute(anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<Consumer<String>>any()))
                .thenReturn(new ExternalProcessResult(0,
                        List.of("irrelevant"), "irrelevant"));

        AudioAnalysisException exception = assertThrows(
                AudioAnalysisException.class,
                () -> analyzer.analyze(audioFile));

        assertEquals(AudioAnalysisException.ErrorCodes
                        .LOUDNESS_ANALYSIS_PARSE_FAILED,
                exception.getErrorCode());
        assertFalse(exception.isRetryable());
    }

    @Test
    void returnsFramesAndSummaryFromSameProcessOutput() throws Exception {
        when(processExecutor.execute(anyList(), anyInt(),
                org.mockito.ArgumentMatchers.<Consumer<String>>any()))
                .thenAnswer(invocation -> {
                    Consumer<String> consumer = invocation.getArgument(2);
                    List.of(
                            "frame:0 pts:0 pts_time:0",
                            "lavfi.r128.M=-25.5",
                            "lavfi.r128.S=-26.0",
                            "lavfi.astats.Overall.RMS_level=-31.5",
                            "lavfi.astats.Overall.Peak_level=-20.0",
                            "lavfi.aspectralstats.1.flatness=0.80",
                            "lavfi.aspectralstats.1.entropy=5.0",
                            "Summary:",
                            "  Integrated loudness:",
                            "    I: -20.0 LUFS",
                            "  Loudness range:",
                            "    LRA: 5.0 LU",
                            "  Sample peak:",
                            "    Peak: -2.0 dBFS",
                            "  True peak:",
                            "    Peak: -1.5 dBFS"
                    ).forEach(consumer);
                    return new ExternalProcessResult(0, List.of(),
                            "truncated");
                });

        LoudnessAnalysis result = analyzer.analyze(audioFile).orElseThrow();

        assertEquals(new java.math.BigDecimal("-20.0"),
                result.metrics().integratedLoudnessLufs());
        assertEquals(1, result.frames().size());
        assertEquals(new java.math.BigDecimal("-25.5"),
                result.frames().getFirst().momentaryLufs());
        assertTrue(result.frames().getFirst().timestampMs() == 0);
        assertEquals(1, result.noiseFrames().size());
        assertEquals(new java.math.BigDecimal("-31.5"),
                result.noiseFrames().getFirst().rmsDbfs());
    }
}
