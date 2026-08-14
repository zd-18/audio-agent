package com.audioagent.file.multipart;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "audio.multipart-upload")
public class MultipartUploadProperties {

    @Min(1)
    private long stateTtlHours = 24;

    @Min(1)
    private long completedRetentionHours = 168;

    @Min(60_000)
    private long cleanupIntervalMs = 3_600_000;
}
