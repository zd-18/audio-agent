package com.audioagent.agent.entity;

import com.audioagent.agent.model.AgentConversationStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_conversation")
public class AgentConversation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long transcriptId;
    private Long audioFileId;
    private String title;
    private AgentConversationStatus status;
    private String modelName;
    private String promptVersion;
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
