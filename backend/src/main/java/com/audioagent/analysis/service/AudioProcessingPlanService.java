package com.audioagent.analysis.service;

import com.audioagent.analysis.vo.ProcessingPlanVO;
import com.audioagent.analysis.processing.ProcessingPlanDraft;

public interface AudioProcessingPlanService {

    ProcessingPlanVO generateForOwner(Long userId, Long taskId);

    ProcessingPlanVO saveAgentPlanForOwner(Long userId, Long taskId,
                                           ProcessingPlanDraft draft);

    ProcessingPlanVO get(Long userId, Long taskId);
}
