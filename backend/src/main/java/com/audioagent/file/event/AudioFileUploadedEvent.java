package com.audioagent.file.event;

public record AudioFileUploadedEvent(
        Long audioFileId,
        Long userId,
        int eventVersion) {

    public static final int CURRENT_VERSION = 1;
}
