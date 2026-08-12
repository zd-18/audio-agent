package com.audioagent.agent.mapper;

import com.audioagent.agent.entity.AgentConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {

    @Select("""
            SELECT * FROM agent_conversation
            WHERE id = #{conversationId}
              AND user_id = #{userId}
              AND deleted = 0
            LIMIT 1
            """)
    AgentConversation selectOwnedById(@Param("userId") Long userId,
                                      @Param("conversationId") Long conversationId);

    @Select("""
            SELECT * FROM agent_conversation
            WHERE id = #{conversationId} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    AgentConversation lockById(@Param("conversationId") Long conversationId);

    @Select("""
            <script>
            SELECT * FROM agent_conversation
            WHERE user_id = #{userId} AND deleted = 0
            <if test="transcriptId != null">
              AND transcript_id = #{transcriptId}
            </if>
            <if test="status != null and status != ''">
              AND status = #{status}
            </if>
            ORDER BY COALESCE(last_message_at, updated_at) DESC,
                     updated_at DESC, id DESC
            </script>
            """)
    IPage<AgentConversation> selectOwnedPage(
            Page<AgentConversation> page,
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId,
            @Param("status") String status);

    @Update("""
            UPDATE agent_conversation
            SET last_message_id = #{messageId},
                last_message_at = #{messageAt},
                updated_at = #{messageAt}
            WHERE id = #{conversationId}
              AND user_id = #{userId}
              AND deleted = 0
              AND (
                last_message_id IS NULL
                OR COALESCE((
                    SELECT m.sequence_no
                    FROM agent_message m
                    WHERE m.id = agent_conversation.last_message_id
                ), 0) < #{sequenceNo}
              )
            """)
    int updateLastMessage(@Param("userId") Long userId,
                          @Param("conversationId") Long conversationId,
                          @Param("messageId") Long messageId,
                          @Param("sequenceNo") Integer sequenceNo,
                          @Param("messageAt") LocalDateTime messageAt);

    @Update("""
            UPDATE agent_conversation
            SET title = #{newTitle}, updated_at = #{updatedAt}
            WHERE id = #{conversationId}
              AND user_id = #{userId}
              AND title = #{defaultTitle}
              AND deleted = 0
            """)
    int updateTitleIfDefault(@Param("userId") Long userId,
                             @Param("conversationId") Long conversationId,
                             @Param("defaultTitle") String defaultTitle,
                             @Param("newTitle") String newTitle,
                             @Param("updatedAt") LocalDateTime updatedAt);
}
