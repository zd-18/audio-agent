package com.audioagent.contentanalysis.mapper;

import com.audioagent.contentanalysis.entity.AudioContentAnalysisResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioContentAnalysisResultMapper
        extends BaseMapper<AudioContentAnalysisResult> {

    @Insert("""
            INSERT INTO audio_content_analysis_result (
                id, user_id, task_id, transcript_id,
                summary_json, key_points_json, chapters_json,
                speech_issues_json, prompt_tokens, completion_tokens,
                total_tokens, model_name, prompt_version,
                created_at, updated_at
            ) VALUES (
                #{id}, #{userId}, #{taskId}, #{transcriptId},
                #{summaryJson,jdbcType=VARCHAR},
                #{keyPointsJson,jdbcType=VARCHAR},
                #{chaptersJson,jdbcType=VARCHAR},
                #{speechIssuesJson,jdbcType=VARCHAR},
                #{promptTokens,jdbcType=INTEGER},
                #{completionTokens,jdbcType=INTEGER},
                #{totalTokens,jdbcType=INTEGER},
                #{modelName}, #{promptVersion},
                #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                transcript_id = VALUES(transcript_id),
                summary_json = VALUES(summary_json),
                key_points_json = VALUES(key_points_json),
                chapters_json = VALUES(chapters_json),
                speech_issues_json = VALUES(speech_issues_json),
                prompt_tokens = VALUES(prompt_tokens),
                completion_tokens = VALUES(completion_tokens),
                total_tokens = VALUES(total_tokens),
                model_name = VALUES(model_name),
                prompt_version = VALUES(prompt_version),
                updated_at = VALUES(updated_at)
            """)
    int upsert(AudioContentAnalysisResult result);

    @Select("""
            SELECT id, user_id, task_id, transcript_id,
                   summary_json, key_points_json, chapters_json,
                   speech_issues_json, prompt_tokens, completion_tokens,
                   total_tokens, model_name, prompt_version,
                   created_at, updated_at
            FROM audio_content_analysis_result
            WHERE task_id = #{taskId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    AudioContentAnalysisResult selectOwnedByTask(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId);

    @Select("""
            SELECT id, user_id, task_id, transcript_id,
                   summary_json, key_points_json, chapters_json,
                   speech_issues_json, prompt_tokens, completion_tokens,
                   total_tokens, model_name, prompt_version,
                   created_at, updated_at
            FROM audio_content_analysis_result
            WHERE task_id = #{taskId}
            LIMIT 1
            """)
    AudioContentAnalysisResult selectByTask(
            @Param("taskId") Long taskId);
}
