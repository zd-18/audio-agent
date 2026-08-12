package com.audioagent.analysis.mq;

import com.audioagent.infrastructure.ffprobe.AnalysisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "audio.analysis.dispatch-mode",
        havingValue = "rabbit"
)
@RequiredArgsConstructor
public class AudioAnalysisRabbitConfig {

    private final ObjectMapper objectMapper;
    private final AnalysisProperties analysisProperties;

    /* ==================== 主交换机／队列 ==================== */

    @Bean
    public DirectExchange audioAnalysisExchange() {
        return ExchangeBuilder.directExchange(
                        AudioAnalysisRabbitConstants.EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue audioAnalysisTaskQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange",
                AudioAnalysisRabbitConstants.DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key",
                AudioAnalysisRabbitConstants.DEAD_ROUTING_KEY);

        return QueueBuilder.durable(
                        AudioAnalysisRabbitConstants.TASK_QUEUE)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding audioAnalysisTaskBinding() {
        return BindingBuilder
                .bind(audioAnalysisTaskQueue())
                .to(audioAnalysisExchange())
                .with(AudioAnalysisRabbitConstants.TASK_ROUTING_KEY);
    }

    /* ==================== 重试交换机／队列 ==================== */

    @Bean
    public DirectExchange audioAnalysisRetryExchange() {
        return ExchangeBuilder.directExchange(
                        AudioAnalysisRabbitConstants.RETRY_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 重试队列：配置消息 TTL，过期后通过 dead-letter-exchange
     * 自动投递回主交换机，实现延迟重试。
     */
    @Bean
    public Queue audioAnalysisRetryQueue() {
        int delayMs = analysisProperties.getRetry()
                .getDelayMilliseconds();

        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", delayMs);
        args.put("x-dead-letter-exchange",
                AudioAnalysisRabbitConstants.EXCHANGE);
        args.put("x-dead-letter-routing-key",
                AudioAnalysisRabbitConstants.TASK_ROUTING_KEY);

        return QueueBuilder.durable(
                        AudioAnalysisRabbitConstants.RETRY_QUEUE)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding audioAnalysisRetryBinding() {
        return BindingBuilder
                .bind(audioAnalysisRetryQueue())
                .to(audioAnalysisRetryExchange())
                .with(AudioAnalysisRabbitConstants.RETRY_ROUTING_KEY);
    }

    /* ==================== 死信交换机／队列 ==================== */

    @Bean
    public DirectExchange audioAnalysisDeadExchange() {
        return ExchangeBuilder.directExchange(
                        AudioAnalysisRabbitConstants.DEAD_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue audioAnalysisDeadQueue() {
        return QueueBuilder.durable(
                        AudioAnalysisRabbitConstants.DEAD_QUEUE)
                .build();
    }

    @Bean
    public Binding audioAnalysisDeadBinding() {
        return BindingBuilder
                .bind(audioAnalysisDeadQueue())
                .to(audioAnalysisDeadExchange())
                .with(AudioAnalysisRabbitConstants.DEAD_ROUTING_KEY);
    }

    /* ==================== 消息转换与模板 ==================== */

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = objectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            if (ack) {
                log.debug(
                        "Message confirmed by broker, messageId={}",
                        correlationData.getId()
                );
            } else {
                log.error(
                        "Message nack by broker, messageId={}, cause={}",
                        correlationData.getId(),
                        cause
                );
            }
        });

        template.setReturnsCallback(returned -> {
            String messageId = returned.getMessage()
                    .getMessageProperties()
                    .getMessageId();
            log.error(
                    "Message returned unroutable, messageId={}, replyCode={}, replyText={}",
                    messageId,
                    returned.getReplyCode(),
                    returned.getReplyText()
            );
        });

        return template;
    }
}
