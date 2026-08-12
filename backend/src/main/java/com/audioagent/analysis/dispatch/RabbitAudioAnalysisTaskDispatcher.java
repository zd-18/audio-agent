package com.audioagent.analysis.dispatch;

import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.enums.AnalysisTaskStatus;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.analysis.mq.AudioAnalysisRabbitConstants;
import com.audioagent.analysis.mq.AudioAnalysisTaskMessage;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "rabbit"
)
@RequiredArgsConstructor
public class RabbitAudioAnalysisTaskDispatcher
        implements AudioAnalysisTaskDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final AudioAnalysisTaskMapper taskMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void dispatch(Long taskId) {
        AudioAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error(
                    "Task not found for rabbit dispatch, taskId={}",
                    taskId
            );
            return;
        }

        AudioAnalysisTaskMessage message =
                AudioAnalysisTaskMessage.firstDispatch(taskId);

        try {
            rabbitTemplate.convertAndSend(
                    AudioAnalysisRabbitConstants.EXCHANGE,
                    AudioAnalysisRabbitConstants.TASK_ROUTING_KEY,
                    message,
                    new CorrelationData(message.getMessageId())
            );

            log.info(
                    "Task dispatched to RabbitMQ, taskId={}, messageId={}",
                    taskId, message.getMessageId()
            );

        } catch (Exception e) {
            log.error(
                    "Failed to dispatch task to RabbitMQ, taskId={}",
                    taskId, e
            );
            failTaskOnDispatchFailure(taskId);
        }
    }

    private void failTaskOnDispatchFailure(Long taskId) {
        try {
            Integer rows = transactionTemplate.execute(s -> {
                LambdaUpdateWrapper<AudioAnalysisTask> w =
                        new LambdaUpdateWrapper<>();
                w.eq(AudioAnalysisTask::getId, taskId)
                        .set(AudioAnalysisTask::getStatus,
                                AnalysisTaskStatus.FAILED)
                        .set(AudioAnalysisTask::getProgress, 0)
                        .set(AudioAnalysisTask::getErrorMessage,
                                "分析任务消息发送失败")
                        .set(AudioAnalysisTask::getLastErrorCode,
                                AudioAnalysisTaskMessage
                                        .firstDispatch(taskId)
                                        .getErrorCode())
                        .set(AudioAnalysisTask::getFinishedAt,
                                LocalDateTime.now())
                        .set(AudioAnalysisTask::getUpdatedAt,
                                LocalDateTime.now());
                return taskMapper.update(null, w);
            });
            if (rows == null || rows != 1) {
                log.error(
                        "Failed to update task after dispatch failure, "
                                + "taskId={}, rows={}",
                        taskId, rows);
            }
        } catch (Exception e) {
            log.error(
                    "Failed to mark task as FAILED after dispatch failure, "
                            + "taskId={}",
                    taskId, e);
        }
    }
}
