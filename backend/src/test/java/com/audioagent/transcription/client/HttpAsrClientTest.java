package com.audioagent.transcription.client;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionRequest;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.exception.TranscriptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HttpAsrClientTest {

    @TempDir Path tempDirectory;
    @Mock RestClient restClient;
    @Mock RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock(answer = Answers.RETURNS_SELF) RestClient.RequestBodySpec requestBodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    private HttpAsrClient client;
    private Path wavFile;

    @BeforeEach
    void setUp() throws Exception {
        client = new HttpAsrClient(restClient);
        wavFile = tempDirectory.resolve("test.wav");
        Files.write(wavFile, new byte[2048]);
    }

    private void stubChain() {
        doReturn(requestBodyUriSpec).when(restClient).post();
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
    }

    @Test
    void validWavConstructsMultipartRequest() {
        stubChain();
        AsrTranscriptionResponse expected = validResponse();
        doReturn(expected).when(responseSpec)
                .body(AsrTranscriptionResponse.class);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        AsrTranscriptionResponse response =
                assertDoesNotThrow(() -> client.transcribe(request));

        assertNotNull(response);
        assertEquals("zh", response.getLanguage());
    }

    @Test
    void multipartContainsCorrectFieldNames() {
        stubChain();
        AsrTranscriptionResponse expected = validResponse();
        doReturn(expected).when(responseSpec)
                .body(AsrTranscriptionResponse.class);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "en", true);
        client.transcribe(request);

        ArgumentCaptor<Object> bodyCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> body =
                (MultiValueMap<String, Object>) bodyCaptor.getValue();
        assertNotNull(body.get("file"),
                "multipart should contain 'file' part");
        assertNotNull(body.get("language"),
                "multipart should contain 'language' part");
        assertNotNull(body.get("enableSpeakerDiarization"),
                "multipart should contain 'enableSpeakerDiarization' part");
    }

    @Test
    void connectionRefusedMapsToServiceUnavailable() {
        doThrow(new ResourceAccessException("Connection refused",
                new ConnectException("Connection refused")))
                .when(restClient).post();

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.ASR_SERVICE_UNAVAILABLE,
                exception.getErrorCode());
    }

    @Test
    void timeoutMapsToAsrTimeout() {
        doThrow(new ResourceAccessException("Read timed out",
                new SocketTimeoutException("Read timed out")))
                .when(restClient).post();

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.ASR_TIMEOUT, exception.getErrorCode());
    }

    @Test
    void asrReturns422MapsToResponseInvalid() {
        stubChain();
        doThrow(new TranscriptionException(
                ErrorCode.ASR_RESPONSE_INVALID, false,
                "音频中未识别到可转写的语音内容或格式无效"))
                .when(responseSpec).body(AsrTranscriptionResponse.class);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.ASR_RESPONSE_INVALID,
                exception.getErrorCode());
    }

    @Test
    void asrReturns200CanDeserializeSuccessfully() {
        stubChain();
        AsrTranscriptionResponse expected = new AsrTranscriptionResponse();
        expected.setLanguage("zh");
        expected.setDurationMs(30000L);
        expected.setFullText("完整转写文本内容");
        expected.setSpeakerCount(2);
        AsrSegmentResponse seg1 = new AsrSegmentResponse();
        seg1.setOrder(1);
        seg1.setStartMs(0L);
        seg1.setEndMs(15000L);
        seg1.setText("第一段文本");
        AsrSegmentResponse seg2 = new AsrSegmentResponse();
        seg2.setOrder(2);
        seg2.setStartMs(15000L);
        seg2.setEndMs(30000L);
        seg2.setText("第二段文本");
        expected.setSegments(List.of(seg1, seg2));
        doReturn(expected).when(responseSpec)
                .body(AsrTranscriptionResponse.class);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        AsrTranscriptionResponse response = client.transcribe(request);

        assertNotNull(response);
        assertEquals("zh", response.getLanguage());
        assertEquals(30000L, response.getDurationMs());
        assertEquals("完整转写文本内容", response.getFullText());
        assertEquals(2, response.getSpeakerCount());
        assertEquals(2, response.getSegments().size());
    }

    @Test
    void jsonParseErrorMapsToResponseInvalid() {
        stubChain();
        doThrow(new HttpMessageNotReadableException("JSON parse error"))
                .when(responseSpec).body(AsrTranscriptionResponse.class);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                wavFile, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.ASR_RESPONSE_INVALID,
                exception.getErrorCode());
    }

    @Test
    void nullFilePathThrowsStandardizationFailed() {
        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                null, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
    }

    @Test
    void emptyFileThrowsStandardizationFailed() throws Exception {
        Path emptyFile = tempDirectory.resolve("empty.wav");
        Files.write(emptyFile, new byte[0]);

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                emptyFile, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
    }

    @Test
    void nonexistentFileThrowsStandardizationFailed() {
        Path nonexistent = tempDirectory.resolve("nonexistent.wav");

        AsrTranscriptionRequest request = new AsrTranscriptionRequest(
                nonexistent, "zh", false);

        TranscriptionException exception = assertThrows(
                TranscriptionException.class,
                () -> client.transcribe(request));

        assertEquals(ErrorCode.AUDIO_STANDARDIZATION_FAILED,
                exception.getErrorCode());
    }

    private static AsrTranscriptionResponse validResponse() {
        AsrTranscriptionResponse response = new AsrTranscriptionResponse();
        response.setLanguage("zh");
        response.setDurationMs(5000L);
        response.setFullText("测试");
        AsrSegmentResponse seg = new AsrSegmentResponse();
        seg.setOrder(1);
        seg.setStartMs(0L);
        seg.setEndMs(5000L);
        seg.setText("测试");
        response.setSegments(List.of(seg));
        return response;
    }
}
