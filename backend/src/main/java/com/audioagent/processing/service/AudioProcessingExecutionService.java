package com.audioagent.processing.service;

import com.audioagent.common.api.PageResult;
import com.audioagent.processing.vo.ProcessingExecutionListVO;
import com.audioagent.processing.vo.ProcessingExecutionVO;

public interface AudioProcessingExecutionService {

    PageResult<ProcessingExecutionListVO> list(Long userId, int current,
                                               int size, String status);

    ProcessingExecutionVO create(Long userId, Long confirmationId);

    ProcessingExecutionVO get(Long userId, Long executionId);

    ProcessingExecutionVO getByTask(Long userId, Long taskId);

    ProcessingExecutionVO retry(Long userId, Long executionId);
}
