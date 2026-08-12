package com.audioagent.analysis.dispatch;

public interface AudioAnalysisTaskDispatcher {

    /**
     * 分发分析任务。
     * local 模式下异步执行，rabbit 模式下发送消息到队列。
     *
     * @param taskId 任务ID
     */
    void dispatch(Long taskId);
}
