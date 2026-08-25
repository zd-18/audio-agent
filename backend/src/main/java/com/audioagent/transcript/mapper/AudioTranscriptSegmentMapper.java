package com.audioagent.transcript.mapper;

import com.audioagent.transcript.entity.AudioTranscriptSegment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AudioTranscriptSegmentMapper extends BaseMapper<AudioTranscriptSegment> {

    @Select("""
            <script>
            SELECT *
            FROM audio_transcript_segment
            WHERE transcript_id = #{transcriptId}
              AND user_id = #{userId}
            <if test="keyword != null and keyword != ''">
              AND text LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY segment_order ASC
            </script>
            """)
    IPage<AudioTranscriptSegment> selectOwnedPage(
            Page<AudioTranscriptSegment> page,
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId,
            @Param("keyword") String keyword);

    @Select("""
            SELECT id, user_id, transcript_id, segment_order,
                   start_ms, end_ms, speaker_label, text, confidence,
                   created_at
            FROM audio_transcript_segment
            WHERE transcript_id = #{transcriptId}
              AND user_id = #{userId}
            ORDER BY segment_order ASC
            """)
    List<AudioTranscriptSegment> selectOwnedAll(
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId);

    @Select("""
            SELECT id, user_id, transcript_id, segment_order,
                   start_ms, end_ms, speaker_label, text, confidence,
                   created_at
            FROM audio_transcript_segment
            WHERE id = #{segmentId}
              AND transcript_id = #{transcriptId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    AudioTranscriptSegment selectOwnedSegment(
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId,
            @Param("segmentId") Long segmentId);

    @Update("""
            UPDATE audio_transcript_segment
            SET text = #{text}, speaker_label = #{speaker}
            WHERE id = #{segmentId}
              AND transcript_id = #{transcriptId}
              AND user_id = #{userId}
            """)
    int updateOwnedContent(
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId,
            @Param("segmentId") Long segmentId,
            @Param("text") String text,
            @Param("speaker") String speaker);
}
