package com.audioagent.processing.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioProcessingExecutionMessage implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private Long executionId;
}
