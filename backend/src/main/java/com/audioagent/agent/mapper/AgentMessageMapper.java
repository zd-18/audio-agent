package com.audioagent.agent.mapper;

import com.audioagent.agent.entity.AgentMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    @Select("""
            SELECT * FROM agent_message
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
            ORDER BY sequence_no ASC
            """)
    IPage<AgentMessage> selectOwnedPageByConversationId(
            Page<AgentMessage> page,
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId);

    @Select("""
            SELECT * FROM agent_message
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND status = 'SUCCESS'
              AND sequence_no < #{beforeSequence}
            ORDER BY sequence_no DESC
            LIMIT #{limit}
            """)
    List<AgentMessage> selectRecentSuccessMessages(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("beforeSequence") int beforeSequence,
            @Param("limit") int limit);

    @Select("""
            SELECT * FROM agent_message
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND role = 'USER'
              AND client_request_id = #{clientRequestId}
            LIMIT 1
            """)
    AgentMessage selectByConversationAndClientRequestId(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT * FROM agent_message
            WHERE conversation_id = #{conversationId}
              AND user_id = #{userId}
              AND role = 'ASSISTANT'
              AND reply_to_message_id = #{userMessageId}
            LIMIT 1
            """)
    AgentMessage selectReplyByUserMessageId(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("userMessageId") Long userMessageId);

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
            FROM agent_message
            WHERE conversation_id = #{conversationId}
            """)
    int selectMaxSequenceNo(@Param("conversationId") Long conversationId);

    @Update("""
            UPDATE agent_message
            SET content = #{content}, status = 'SUCCESS',
                model_name = #{modelName},
                prompt_version = #{promptVersion},
                prompt_tokens = #{promptTokens},
                completion_tokens = #{completionTokens},
                total_tokens = #{totalTokens},
                finished_at = #{finishedAt}, updated_at = #{finishedAt},
                failure_code = NULL, failure_message = NULL
            WHERE id = #{messageId}
              AND user_id = #{userId}
              AND status = 'PROCESSING'
            """)
    int updateSuccess(@Param("userId") Long userId,
                      @Param("messageId") Long messageId,
                      @Param("content") String content,
                      @Param("modelName") String modelName,
                      @Param("promptVersion") String promptVersion,
                      @Param("promptTokens") Integer promptTokens,
                      @Param("completionTokens") Integer completionTokens,
                      @Param("totalTokens") Integer totalTokens,
                      @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE agent_message
            SET status = 'FAILED', failure_code = #{failureCode},
                failure_message = #{failureMessage},
                finished_at = #{finishedAt}, updated_at = #{finishedAt}
            WHERE id = #{messageId}
              AND user_id = #{userId}
              AND status = 'PROCESSING'
            """)
    int updateFailed(@Param("userId") Long userId,
                     @Param("messageId") Long messageId,
                     @Param("failureCode") String failureCode,
                     @Param("failureMessage") String failureMessage,
                     @Param("finishedAt") LocalDateTime finishedAt);
}
