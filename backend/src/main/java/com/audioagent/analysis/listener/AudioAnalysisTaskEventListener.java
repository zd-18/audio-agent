package com.audioagent.analysis.listener;

import com.audioagent.analysis.dispatch.AudioAnalysisTaskDispatcher;
import com.audioagent.analysis.event.AudioAnalysisTaskCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioAnalysisTaskEventListener {

    private final AudioAnalysisTaskDispatcher dispatcher;

    /**
     * 在创建任务的事务提交后通过 Dispatcher 分发任务。
     * phase=AFTER_COMMIT 确保异步线程或消费者能读到已提交的任务记录。
     * <p>
     * local 模式：LocalDispatcher 通过 @Async 异步调用 Executor。
     * rabbit 模式：RabbitDispatcher 发送消息到 RabbitMQ 队列。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskCreated(AudioAnalysisTaskCreatedEvent event) {
        log.info(
                "Received analysis task event, taskId={}, audioFileId={}",
                event.getTaskId(),
                event.getAudioFileId()
        );
        dispatcher.dispatch(event.getTaskId());
    }
}
