package com.audioagent.file.vo;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class AudioPlaybackUrlVO {

    String fileId;
    String fileName;
    String mimeType;
    String playbackUrl;
    OffsetDateTime expiresAt;
    int expiresInSeconds;
}
