package com.audioagent.transcription.mapper;

import com.audioagent.transcription.entity.AudioTranscript;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioTranscriptMapper extends BaseMapper<AudioTranscript> {

    @Select("""
            SELECT * FROM audio_transcript
            WHERE transcription_task_id = #{taskId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    AudioTranscript selectByTaskAndUser(@Param("taskId") Long taskId,
                                        @Param("userId") Long userId);

    @Select("""
            SELECT * FROM audio_transcript
            WHERE id = #{transcriptId} AND user_id = #{userId}
            LIMIT 1
            """)
    AudioTranscript selectOwned(@Param("userId") Long userId,
                                @Param("transcriptId") Long transcriptId);

    @Select("""
            SELECT * FROM audio_transcript
            WHERE id = #{transcriptId} AND user_id = #{userId}
            LIMIT 1
            FOR UPDATE
            """)
    AudioTranscript selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId);
}
