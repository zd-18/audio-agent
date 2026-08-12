package com.audioagent.agent.entity;

import com.audioagent.agent.model.AgentMessageRole;
import com.audioagent.agent.model.AgentMessageStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message")
public class AgentMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long conversationId;
    private Integer sequenceNo;
    private AgentMessageRole role;
    private String content;
    private AgentMessageStatus status;
    private Long replyToMessageId;
    private String clientRequestId;
    private String modelName;
    private String promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
