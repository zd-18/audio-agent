package com.audioagent.file.service;

import com.audioagent.file.vo.AudioVersionChainVO;

public interface AudioVersionService {

    AudioVersionChainVO getByAudioFile(Long userId, Long audioFileId);

    AudioVersionChainVO getByTask(Long userId, Long taskId);
}
