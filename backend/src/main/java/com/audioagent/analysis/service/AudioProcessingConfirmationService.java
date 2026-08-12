package com.audioagent.analysis.service;

import com.audioagent.analysis.dto.UpdateProcessingStepConfirmationRequest;
import com.audioagent.analysis.vo.ProcessingConfirmationVO;

public interface AudioProcessingConfirmationService {

    ProcessingConfirmationVO create(Long userId, Long taskId);

    ProcessingConfirmationVO getCurrent(Long userId, Long taskId);

    ProcessingConfirmationVO.Step updateStep(
            Long userId, Long confirmationId, Long stepConfirmationId,
            UpdateProcessingStepConfirmationRequest request);

    ProcessingConfirmationVO confirm(Long userId, Long confirmationId);

    ProcessingConfirmationVO cancel(Long userId, Long confirmationId);
}

