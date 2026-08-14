package com.audioagent.agent.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_processing_workflow")
public class AgentProcessingWorkflow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long taskId;
    private Long audioFileId;
    private Long planId;
    private Long confirmationId;
    private Long executionId;
    private Long resultFileId;
    private String workflowStatus;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}
