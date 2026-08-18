package com.audioagent.analysis.listener;

import com.audioagent.analysis.dispatch.AudioAnalysisTaskDispatcher;
import com.audioagent.analysis.event.AudioAnalysisTaskCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "local",
        matchIfMissing = true)
@RequiredArgsConstructor
public class AudioAnalysisTaskEventListener {

    private final AudioAnalysisTaskDispatcher dispatcher;

    /**
     * 在创建任务的事务提交后通过 Dispatcher 分发任务。
     * phase=AFTER_COMMIT 确保异步线程或消费者能读到已提交的任务记录。
     * <p>
     * 仅用于 local 模式，通过 @Async 异步调用 Executor。Rabbit 模式的
     * 首次投递和人工重试统一由事务 Outbox Worker 直接发送到最终队列。
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
