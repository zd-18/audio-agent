package com.audioagent.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message_citation")
public class AgentMessageCitation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long messageId;
    private Long transcriptId;
    private Long segmentId;
    private Integer segmentOrder;
    private Long startMs;
    private Long endMs;
    private String quote;
    private Integer citationOrder;
    private LocalDateTime createdAt;
}
