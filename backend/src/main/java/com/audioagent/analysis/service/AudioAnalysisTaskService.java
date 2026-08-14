package com.audioagent.analysis.service;

import com.audioagent.analysis.dto.CreateTaskRequest;
import com.audioagent.analysis.vo.TaskVO;
import com.audioagent.analysis.vo.TaskListVO;
import com.audioagent.common.api.PageResult;

public interface AudioAnalysisTaskService {

    PageResult<TaskListVO> listTasks(Long userId, int current, int size,
                                     String status,
                                     Long audioFileId, String analysisType,
                                     String keyword);

    /**
     * 创建音频分析任务。
     */
    TaskVO createTask(CreateTaskRequest request);

    TaskVO createTaskFromUploadedFile(Long audioFileId, Long userId,
                                      Long sourceEventId);

    /**
     * 查询任务详情。
     */
    TaskVO getTaskDetail(Long taskId);

    /**
     * 人工重试 FAILED 任务。
     * 将任务重置为 PENDING 并重新分发。
     */
    TaskVO retryTask(Long taskId);
}
