package com.audioagent.analysis.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioAnalysisTaskMessage {

    private Long taskId;

    private String messageId;

    private String originalMessageId;

    private Integer retryCount;

    private LocalDateTime publishedAt;

    /**
     * 仅死信消息使用。
     */
    private String errorCode;

    private String errorMessage;

    private LocalDateTime failedAt;

    /**
     * 首次投递。messageId 同时作为 originalMessageId。
     */
    public static AudioAnalysisTaskMessage firstDispatch(Long taskId) {
        String messageId = java.util.UUID.randomUUID().toString();
        return AudioAnalysisTaskMessage.builder()
                .taskId(taskId)
                .messageId(messageId)
                .originalMessageId(messageId)
                .retryCount(0)
                .publishedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 重试投递。messageId 重新生成，originalMessageId 保持不变。
     */
    public static AudioAnalysisTaskMessage retryDispatch(
            AudioAnalysisTaskMessage original, int newRetryCount) {
        return AudioAnalysisTaskMessage.builder()
                .taskId(original.getTaskId())
                .messageId(java.util.UUID.randomUUID().toString())
                .originalMessageId(original.getOriginalMessageId())
                .retryCount(newRetryCount)
                .publishedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 死信消息。
     */
    public static AudioAnalysisTaskMessage deadLetter(
            AudioAnalysisTaskMessage original,
            String errorCode,
            String errorMessage) {
        return AudioAnalysisTaskMessage.builder()
                .taskId(original.getTaskId())
                .messageId(java.util.UUID.randomUUID().toString())
                .originalMessageId(original.getOriginalMessageId())
                .retryCount(original.getRetryCount())
                .publishedAt(LocalDateTime.now())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .failedAt(LocalDateTime.now())
                .build();
    }
}
