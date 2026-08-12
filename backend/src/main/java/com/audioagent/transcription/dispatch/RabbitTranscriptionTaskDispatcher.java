package com.audioagent.transcription.dispatch;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.transcription.mq.TranscriptionRabbitConstants;
import com.audioagent.transcription.mq.TranscriptionTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitTranscriptionTaskDispatcher
        implements TranscriptionTaskDispatcher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void dispatch(Long taskId) {
        TranscriptionTaskMessage message =
                TranscriptionTaskMessage.first(taskId);
        try {
            rabbitTemplate.convertAndSend(
                    TranscriptionRabbitConstants.EXCHANGE,
                    TranscriptionRabbitConstants.TASK_ROUTING_KEY,
                    message,
                    new CorrelationData(message.getMessageId()));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.TRANSCRIPTION_FAILED,
                    "转写任务暂时无法提交，请稍后重试");
        }
    }
}
