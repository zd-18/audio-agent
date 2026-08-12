package com.audioagent.processing.dispatch;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.processing.mapper.AudioProcessingExecutionMapper;
import com.audioagent.processing.mq.AudioProcessingExecutionMessage;
import com.audioagent.processing.mq.AudioProcessingRabbitConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitAudioProcessingExecutionDispatcher
        implements AudioProcessingExecutionDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final AudioProcessingExecutionMapper executionMapper;

    @Override
    public void dispatch(Long executionId) {
        try {
            rabbitTemplate.convertAndSend(
                    AudioProcessingRabbitConstants.EXCHANGE,
                    AudioProcessingRabbitConstants.EXECUTE_ROUTING_KEY,
                    new AudioProcessingExecutionMessage(executionId));
            executionMapper.markQueued(executionId, LocalDateTime.now());
            log.info("Processing execution dispatched, executionId={}",
                    executionId);
        } catch (RuntimeException e) {
            executionMapper.markDispatchFailed(executionId,
                    ErrorCode.PROCESSING_EXECUTION_FAILED.name(),
                    "Execution message could not be queued",
                    LocalDateTime.now());
            log.error("Processing execution dispatch failed, executionId={}",
                    executionId, e);
        }
    }
}
