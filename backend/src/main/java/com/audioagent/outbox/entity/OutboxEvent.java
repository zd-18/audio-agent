package com.audioagent.outbox.entity;

import com.audioagent.outbox.model.OutboxEventStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private OutboxEventStatus status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private String lockOwner;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
