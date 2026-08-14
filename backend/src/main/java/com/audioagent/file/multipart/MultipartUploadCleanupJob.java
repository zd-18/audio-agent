package com.audioagent.file.multipart;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MultipartUploadCleanupJob {

    private final MultipartUploadService multipartUploadService;

    @Scheduled(fixedDelayString =
            "${audio.multipart-upload.cleanup-interval-ms:3600000}")
    public void cleanupExpiredUploads() {
        multipartUploadService.cleanupExpiredUploads();
    }
}
