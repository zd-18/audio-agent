package com.audioagent.file.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.common.api.ApiResponse;
import java.io.IOException;
import com.audioagent.common.api.PageResult;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.dto.RenameAudioFileRequest;
import com.audioagent.file.multipart.MultipartUploadService;
import com.audioagent.file.multipart.dto.MultipartUploadInitRequest;
import com.audioagent.file.multipart.vo.MultipartChunkVO;
import com.audioagent.file.multipart.vo.MultipartUploadCompleteVO;
import com.audioagent.file.multipart.vo.MultipartUploadInitVO;
import com.audioagent.file.multipart.vo.MultipartUploadProgressVO;
import com.audioagent.file.service.AudioFileService;
import com.audioagent.file.service.AudioVersionService;
import com.audioagent.file.vo.AudioFileVO;
import com.audioagent.file.vo.AudioFileListVO;
import com.audioagent.file.vo.AudioPlaybackUrlVO;
import com.audioagent.file.vo.AudioVersionChainVO;
import com.audioagent.infrastructure.minio.MinioStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class AudioFileController {

    private final AudioFileService audioFileService;
    private final AudioVersionService audioVersionService;
    private final MultipartUploadService multipartUploadService;
    private final MinioStorageService minioStorageService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResult<AudioFileListVO>> listFiles(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioFileService.listFiles(
                userId, current, size, keyword, status));
    }

    @GetMapping("/recycle-bin")
    public ApiResponse<PageResult<AudioFileListVO>> listRecycleBin(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioFileService.listRecycleBin(
                userId, current, size, keyword));
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<AudioFileVO> upload(
            @RequestPart("file") MultipartFile file
    ) {
        Long userId = currentUserProvider.requireUserId();
        AudioFileVO result =
                audioFileService.upload(userId, file);

        return ApiResponse.success(result);
    }

    @PostMapping("/multipart/init")
    public ApiResponse<MultipartUploadInitVO> initializeMultipartUpload(
            @RequestBody MultipartUploadInitRequest request
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                multipartUploadService.initialize(userId, request));
    }

    @PutMapping(
            value = "/multipart/{uploadId}/chunks/{chunkIndex}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<MultipartChunkVO> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            @RequestParam long chunkSize,
            @RequestPart("chunk") MultipartFile chunk
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(multipartUploadService.uploadChunk(
                userId, uploadId, chunkIndex, chunkSize, chunk));
    }

    @GetMapping("/multipart/{uploadId}")
    public ApiResponse<MultipartUploadProgressVO> getMultipartProgress(
            @PathVariable String uploadId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                multipartUploadService.progress(userId, uploadId));
    }

    @PostMapping("/multipart/{uploadId}/complete")
    public ApiResponse<MultipartUploadCompleteVO> completeMultipartUpload(
            @PathVariable String uploadId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                multipartUploadService.complete(userId, uploadId));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<AudioFileVO> getFileDetail(
            @PathVariable("fileId") Long fileId
    ) {
        Long userId = currentUserProvider.requireUserId();
        AudioFileVO result =
                audioFileService.getFileDetail(userId, fileId);

        return ApiResponse.success(result);
    }

    @PatchMapping("/{fileId}/name")
    public ApiResponse<AudioFileVO> rename(
            @PathVariable Long fileId,
            @Valid @RequestBody RenameAudioFileRequest request
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioFileService.rename(
                userId, fileId, request.getFileName()));
    }

    @PostMapping("/{fileId}/archive")
    public ApiResponse<AudioFileVO> archive(@PathVariable Long fileId) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioFileService.archive(userId, fileId));
    }

    @PostMapping("/{fileId}/restore-archive")
    public ApiResponse<AudioFileVO> restoreArchive(
            @PathVariable Long fileId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                audioFileService.restoreArchive(userId, fileId));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> moveToRecycleBin(@PathVariable Long fileId) {
        Long userId = currentUserProvider.requireUserId();
        audioFileService.moveToRecycleBin(userId, fileId);
        return ApiResponse.success(null);
    }

    @PostMapping("/recycle-bin/{fileId}/restore")
    public ApiResponse<AudioFileVO> restoreFromRecycleBin(
            @PathVariable Long fileId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(audioFileService.restoreFromRecycleBin(
                userId, fileId));
    }

    @DeleteMapping("/recycle-bin/{fileId}")
    public ApiResponse<Void> purge(@PathVariable Long fileId) {
        Long userId = currentUserProvider.requireUserId();
        audioFileService.purge(userId, fileId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{fileId}/playback-url")
    public ApiResponse<AudioPlaybackUrlVO> getPlaybackUrl(
            @PathVariable("fileId") Long fileId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                audioFileService.getPlaybackUrl(userId, fileId)
        );
    }

    @GetMapping("/{fileId}/versions")
    public ApiResponse<AudioVersionChainVO> getVersions(
            @PathVariable("fileId") Long fileId
    ) {
        Long userId = currentUserProvider.requireUserId();
        return ApiResponse.success(
                audioVersionService.getByAudioFile(userId, fileId));
    }

    @GetMapping("/{fileId}/download")
public ResponseEntity<StreamingResponseBody> download(
        @PathVariable("fileId") Long fileId
) {
    Long userId = currentUserProvider.requireUserId();
    AudioFile audioFile =
            audioFileService.getFileForDownload(userId, fileId);

    StreamingResponseBody stream = outputStream -> {
        try (InputStream inputStream =
                     minioStorageService.getObject(
                             audioFile.getObjectKey()
                     )) {

            inputStream.transferTo(outputStream);
            outputStream.flush();

        } catch (Exception e) {
            log.error(
                    "Download streaming failed, fileId={}, objectKey={}",
                    fileId,
                    audioFile.getObjectKey(),
                    e
            );

            /*
             * 不要只记录日志后吞掉异常。
             * 否则客户端可能收到 HTTP 200，
             * 但下载到的是残缺文件。
             */
            throw new IOException(
                    "Failed to stream audio file",
                    e
            );
        }
    };

    String downloadFileName =
            sanitizeDownloadFileName(audioFile.getOriginalName());

    ContentDisposition contentDisposition =
            ContentDisposition.attachment()
                    .filename(downloadFileName)
                    .build();

    String mimeType =
            audioFile.getMimeType() != null
                    && !audioFile.getMimeType().isBlank()
                    ? audioFile.getMimeType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;

    MediaType mediaType;
    try {
        mediaType = MediaType.parseMediaType(mimeType);
    } catch (IllegalArgumentException e) {
        mediaType = MediaType.APPLICATION_OCTET_STREAM;
    }

    return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(audioFile.getSizeBytes())
            .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    contentDisposition.toString()
            )
            .body(stream);
}

private String sanitizeDownloadFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
        return "audio-download.wav";
    }

    String sanitized = fileName
            .replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_")
            .trim();

    return sanitized.isBlank()
            ? "audio-download.wav"
            : sanitized;
}
}
