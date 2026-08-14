package com.audioagent.agent.workflow.mapper;

import com.audioagent.agent.workflow.entity.AgentProcessingWorkflow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentProcessingWorkflowMapper
        extends BaseMapper<AgentProcessingWorkflow> {

    @Insert("""
            INSERT IGNORE INTO agent_processing_workflow (
                id, user_id, conversation_id, user_message_id,
                assistant_message_id, task_id, audio_file_id,
                workflow_status, created_at, updated_at
            ) VALUES (
                #{id}, #{userId}, #{conversationId}, #{userMessageId},
                #{assistantMessageId}, #{taskId}, #{audioFileId},
                #{workflowStatus}, #{createdAt}, #{updatedAt}
            )
            """)
    int insertIgnore(AgentProcessingWorkflow workflow);

    @Select("""
            SELECT * FROM agent_processing_workflow
            WHERE user_id = #{userId} AND user_message_id = #{userMessageId}
            """)
    AgentProcessingWorkflow selectByUserMessageId(
            @Param("userId") Long userId,
            @Param("userMessageId") Long userMessageId);

    @Select("""
            SELECT * FROM agent_processing_workflow
            WHERE id = #{workflowId} FOR UPDATE
            """)
    AgentProcessingWorkflow selectByIdForUpdate(
            @Param("workflowId") Long workflowId);

    @Select("""
            SELECT * FROM agent_processing_workflow
            WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<AgentProcessingWorkflow> selectByConversation(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId);

    @Update("""
            UPDATE agent_processing_workflow
            SET plan_id = #{planId}, confirmation_id = #{confirmationId},
                workflow_status = 'WAITING_CONFIRMATION',
                failure_reason = NULL, updated_at = #{now}
            WHERE id = #{workflowId} AND workflow_status = 'PLANNING'
            """)
    int markWaitingConfirmation(@Param("workflowId") Long workflowId,
                                @Param("planId") Long planId,
                                @Param("confirmationId") Long confirmationId,
                                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE agent_processing_workflow
            SET execution_id = #{executionId}, workflow_status = 'EXECUTING',
                failure_reason = NULL, updated_at = #{now}
            WHERE id = #{workflowId}
              AND workflow_status = 'WAITING_CONFIRMATION'
            """)
    int markExecuting(@Param("workflowId") Long workflowId,
                      @Param("executionId") Long executionId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE agent_processing_workflow
            SET workflow_status = #{status}, result_file_id = #{resultFileId},
                failure_reason = #{failureReason}, updated_at = #{now},
                finished_at = #{finishedAt}
            WHERE id = #{workflowId}
            """)
    int updateOutcome(@Param("workflowId") Long workflowId,
                      @Param("status") String status,
                      @Param("resultFileId") Long resultFileId,
                      @Param("failureReason") String failureReason,
                      @Param("now") LocalDateTime now,
                      @Param("finishedAt") LocalDateTime finishedAt);
}
