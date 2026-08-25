package com.audioagent.file.service;

import com.audioagent.common.api.PageResult;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.vo.AudioFileListVO;
import com.audioagent.file.vo.AudioFileVO;
import com.audioagent.file.vo.AudioPlaybackUrlVO;
import org.springframework.web.multipart.MultipartFile;

public interface AudioFileService {

    PageResult<AudioFileListVO> listFiles(Long userId, int current, int size,
                                          String keyword, String status);

    /**
     * 上传原始音频或视频文件。
     *
     * @param userId 当前用户ID
     * @param file   上传文件
     * @return 文件信息
     */
    AudioFileVO upload(Long userId, MultipartFile file);

    /**
     * 根据文件ID查询文件详情。
     *
     * @param userId 当前用户ID
     * @param fileId 文件ID
     * @return 文件信息
     */
    AudioFileVO getFileDetail(Long userId, Long fileId);

    /**
     * 校验文件归属和状态，返回可下载的文件实体。
     *
     * @param userId 当前用户ID
     * @param fileId 文件ID
     * @return 文件实体
     */
    AudioFile getFileForDownload(Long userId, Long fileId);

    /**
     * 校验文件归属和可用状态，生成短期播放地址。
     */
    AudioPlaybackUrlVO getPlaybackUrl(Long userId, Long fileId);

    AudioFileVO rename(Long userId, Long fileId, String fileName);

    AudioFileVO archive(Long userId, Long fileId);

    AudioFileVO restoreArchive(Long userId, Long fileId);

    PageResult<AudioFileListVO> listRecycleBin(
            Long userId, int current, int size, String keyword);

    void moveToRecycleBin(Long userId, Long fileId);

    AudioFileVO restoreFromRecycleBin(Long userId, Long fileId);

    void purge(Long userId, Long fileId);
}
