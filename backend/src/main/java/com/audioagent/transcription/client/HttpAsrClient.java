package com.audioagent.transcription.client;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.dto.AsrTranscriptionRequest;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.exception.TranscriptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class HttpAsrClient implements AsrClient {

    private static final String ASR_URI = "/internal/asr/transcribe";

    private final RestClient restClient;

    public HttpAsrClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AsrTranscriptionResponse transcribe(
            AsrTranscriptionRequest request) {
        Path filePath = request.file();
        validateInputFile(filePath);

        File file = filePath.toFile();
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new FileSystemResource(file))
                .filename("audio.wav")
                .contentType(MediaType.parseMediaType("audio/wav"));
        body.part("language", request.language());
        body.part("enableSpeakerDiarization",
                Boolean.toString(request.enableSpeakerDiarization()));

        log.info("ASR request building, endpoint={}, fileSize={}, "
                        + "language={}, enableSpeakerDiarization={}",
                ASR_URI, file.length(), request.language(),
                request.enableSpeakerDiarization());

        long startedAt = System.nanoTime();
        try {
            AsrTranscriptionResponse response = restClient.post()
                    .uri(ASR_URI)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (httpRequest, httpResponse) -> {
                                int status = httpResponse.getStatusCode()
                                        .value();
                                if (status == 408) {
                                    throw new TranscriptionException(
                                            ErrorCode.ASR_TIMEOUT, true,
                                            "语音识别超时，请稍后重试");
                                }
                                if (status == 400 || status == 422) {
                                    throw new TranscriptionException(
                                            ErrorCode.ASR_RESPONSE_INVALID,
                                            false,
                                            "音频中未识别到可转写的语音内容或格式无效");
                                }
                                if (status == 503) {
                                    throw new TranscriptionException(
                                            ErrorCode.ASR_SERVICE_UNAVAILABLE,
                                            true,
                                            "语音识别服务暂时不可用");
                                }
                                throw new TranscriptionException(
                                        ErrorCode.ASR_SERVICE_UNAVAILABLE,
                                        httpResponse.getStatusCode()
                                                .is5xxServerError(),
                                        "语音识别服务暂时不可用");
                            })
                    .body(AsrTranscriptionResponse.class);
            log.info("ASR request completed, elapsedMs={}, "
                            + "fullTextLength={}, segmentCount={}, "
                            + "durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    response == null || response.getFullText() == null
                            ? 0 : response.getFullText().length(),
                    response == null || response.getSegments() == null
                            ? null : response.getSegments().size(),
                    response == null ? null : response.getDurationMs());
            return response;
        } catch (TranscriptionException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (hasCause(e, SocketTimeoutException.class)) {
                throw new TranscriptionException(ErrorCode.ASR_TIMEOUT, true,
                        "语音识别超时，请稍后重试", e);
            }
            if (hasCause(e, ConnectException.class)) {
                throw new TranscriptionException(
                        ErrorCode.ASR_SERVICE_UNAVAILABLE, true,
                        "语音识别服务暂时不可用", e);
            }
            throw new TranscriptionException(
                    ErrorCode.ASR_SERVICE_UNAVAILABLE, true,
                    "语音识别服务暂时不可用", e);
        } catch (HttpMessageNotReadableException e) {
            throw new TranscriptionException(
                    ErrorCode.ASR_RESPONSE_INVALID, false,
                    "语音识别结果格式不正确", e);
        } catch (RestClientException e) {
            throw new TranscriptionException(
                    ErrorCode.ASR_SERVICE_UNAVAILABLE, true,
                    "语音识别服务暂时不可用", e);
        }
    }

    private void validateInputFile(Path filePath) {
        if (filePath == null
                || !Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {
            throw new TranscriptionException(
                    ErrorCode.AUDIO_STANDARDIZATION_FAILED, false,
                    "标准化音频文件无效");
        }
        try {
            if (Files.size(filePath) <= 0) {
                throw new TranscriptionException(
                        ErrorCode.AUDIO_STANDARDIZATION_FAILED, false,
                        "标准化音频文件无效");
            }
        } catch (TranscriptionException e) {
            throw e;
        } catch (Exception e) {
            throw new TranscriptionException(
                    ErrorCode.AUDIO_STANDARDIZATION_FAILED, false,
                    "标准化音频文件无效", e);
        }
    }

    private boolean hasCause(Throwable error,
                             Class<? extends Throwable> causeType) {
        Throwable current = error;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
