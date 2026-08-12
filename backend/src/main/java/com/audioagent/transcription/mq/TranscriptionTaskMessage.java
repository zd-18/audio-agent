package com.audioagent.transcription.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String messageId;
    private Integer retryCount;

    public static TranscriptionTaskMessage first(Long taskId) {
        return TranscriptionTaskMessage.builder()
                .taskId(taskId)
                .messageId(UUID.randomUUID().toString())
                .retryCount(0)
                .build();
    }

    public static TranscriptionTaskMessage retry(
            TranscriptionTaskMessage source, int retryCount) {
        return TranscriptionTaskMessage.builder()
                .taskId(source.getTaskId())
                .messageId(UUID.randomUUID().toString())
                .retryCount(retryCount)
                .build();
    }
}
