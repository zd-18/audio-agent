package com.audioagent.agent.mapper;

import com.audioagent.agent.entity.AgentMessageCitation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentMessageCitationMapper
        extends BaseMapper<AgentMessageCitation> {

    @Insert("""
            <script>
            INSERT INTO agent_message_citation (
                id, user_id, conversation_id, message_id, transcript_id,
                segment_id, segment_order, start_ms, end_ms, quote,
                citation_order, created_at
            ) VALUES
            <foreach collection="citations" item="item" separator=",">
            (
                #{item.id}, #{item.userId}, #{item.conversationId},
                #{item.messageId}, #{item.transcriptId}, #{item.segmentId},
                #{item.segmentOrder}, #{item.startMs}, #{item.endMs},
                #{item.quote}, #{item.citationOrder}, #{item.createdAt}
            )
            </foreach>
            </script>
            """)
    int batchInsert(@Param("citations") List<AgentMessageCitation> citations);

    @Select("""
            SELECT * FROM agent_message_citation
            WHERE message_id = #{messageId} AND user_id = #{userId}
            ORDER BY citation_order ASC
            """)
    List<AgentMessageCitation> selectByMessageId(
            @Param("userId") Long userId,
            @Param("messageId") Long messageId);

    @Select("""
            <script>
            SELECT * FROM agent_message_citation
            WHERE user_id = #{userId}
              AND message_id IN
              <foreach collection="messageIds" item="id"
                       open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY message_id ASC, citation_order ASC
            </script>
            """)
    List<AgentMessageCitation> selectByMessageIds(
            @Param("userId") Long userId,
            @Param("messageIds") List<Long> messageIds);
}
