package com.audioagent.analysis.service;

import com.audioagent.analysis.vo.ProcessingPlanVO;

public interface AudioProcessingPlanService {

    ProcessingPlanVO generate(Long taskId);

    ProcessingPlanVO generateForOwner(Long userId, Long taskId);

    ProcessingPlanVO get(Long taskId);
}
