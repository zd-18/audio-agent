package com.audioagent.contentanalysis.dispatch;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.contentanalysis.mq.ContentAnalysisRabbitConstants;
import com.audioagent.contentanalysis.mq.ContentAnalysisTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RabbitContentAnalysisTaskDispatcher
        implements ContentAnalysisTaskDispatcher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void dispatch(Long taskId) {
        try {
            rabbitTemplate.convertAndSend(
                    ContentAnalysisRabbitConstants.EXCHANGE,
                    ContentAnalysisRabbitConstants.TASK_ROUTING_KEY,
                    new ContentAnalysisTaskMessage(taskId),
                    new CorrelationData(UUID.randomUUID().toString()));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "智能分析任务暂时无法提交，请稍后重试");
        }
    }
}
