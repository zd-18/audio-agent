package com.audioagent.infrastructure.ffprobe;

import java.nio.file.Path;

public interface AudioMetadataService {

    /**
     * 从音频或视频文件提取元数据。
     * 提取失败时返回 durationMs=null，不抛异常。
     *
     * @param tempFile 本地临时文件路径
     * @return 音频元数据
     */
    AudioMetadata extractMetadata(Path tempFile);
}
