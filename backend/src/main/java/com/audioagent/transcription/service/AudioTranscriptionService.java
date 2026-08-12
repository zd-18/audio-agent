package com.audioagent.transcription.service;

import com.audioagent.common.api.PageResult;
import com.audioagent.transcription.dto.CreateTranscriptionTaskRequest;
import com.audioagent.transcription.vo.TranscriptSegmentVO;
import com.audioagent.transcription.vo.TranscriptVO;
import com.audioagent.transcription.vo.TranscriptionTaskVO;

public interface AudioTranscriptionService {

    TranscriptionTaskVO create(Long userId,
                               CreateTranscriptionTaskRequest request);

    PageResult<TranscriptionTaskVO> list(Long userId, int current, int size,
                                         String status, Long audioFileId);

    TranscriptionTaskVO get(Long userId, Long taskId);

    TranscriptionTaskVO retry(Long userId, Long taskId);

    TranscriptVO getTranscript(Long userId, Long taskId);

    PageResult<TranscriptSegmentVO> listSegments(
            Long userId, Long transcriptId, int current, int size,
            String keyword);
}
