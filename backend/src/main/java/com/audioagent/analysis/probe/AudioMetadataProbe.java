package com.audioagent.analysis.probe;

import java.nio.file.Path;

public interface AudioMetadataProbe {

    /**
     * 对本地音频文件执行元数据探测。
     *
     * @param filePath 本地文件路径
     * @return 音频元数据
     * @throws RuntimeException 探测失败时抛出
     */
    AudioMetadata probe(Path filePath);
}
