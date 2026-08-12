package com.audioagent.contentanalysis.mapper;

import com.audioagent.contentanalysis.entity.AudioContentAnalysisTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AudioContentAnalysisTaskMapper
        extends BaseMapper<AudioContentAnalysisTask> {

    @Select("""
            SELECT id, user_id, transcript_id, status,
                   analysis_types AS analysis_types_json,
                   summary_style, progress_percent, model_name,
                   prompt_version, retry_count, failure_code,
                   failure_message, started_at, finished_at,
                   created_at, updated_at
            FROM audio_content_analysis_task
            WHERE id = #{taskId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    AudioContentAnalysisTask selectOwned(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId);

    @Select("""
            SELECT id, user_id, transcript_id, status,
                   analysis_types AS analysis_types_json,
                   summary_style, progress_percent, model_name,
                   prompt_version, retry_count, failure_code,
                   failure_message, started_at, finished_at,
                   created_at, updated_at
            FROM audio_content_analysis_task
            WHERE user_id = #{userId}
              AND transcript_id = #{transcriptId}
              AND analysis_types = #{analysisTypes}
              AND summary_style = #{summaryStyle}
              AND status IN ('PENDING', 'RUNNING', 'SUCCESS')
              AND created_at >= #{cutoff}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    AudioContentAnalysisTask selectReusable(
            @Param("userId") Long userId,
            @Param("transcriptId") Long transcriptId,
            @Param("analysisTypes") String analysisTypes,
            @Param("summaryStyle") String summaryStyle,
            @Param("cutoff") LocalDateTime cutoff);

    @Update("""
            UPDATE audio_content_analysis_task
            SET status = 'RUNNING',
                progress_percent = 5,
                started_at = COALESCE(started_at, #{now}),
                finished_at = NULL,
                failure_code = NULL,
                failure_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'PENDING'
            """)
    int claim(@Param("taskId") Long taskId,
              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_content_analysis_task
            SET progress_percent = #{progress},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
            """)
    int updateProgress(@Param("taskId") Long taskId,
                       @Param("progress") int progress,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_content_analysis_task
            SET status = 'PENDING',
                progress_percent = 0,
                retry_count = #{retryCount},
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                finished_at = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
            """)
    int scheduleRetry(
            @Param("taskId") Long taskId,
            @Param("retryCount") int retryCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_content_analysis_task
            SET status = 'FAILED',
                progress_percent = 0,
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND status != 'SUCCESS'
            """)
    int markFailed(
            @Param("taskId") Long taskId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_content_analysis_task
            SET status = 'PENDING',
                progress_percent = 0,
                retry_count = 0,
                failure_code = NULL,
                failure_message = NULL,
                started_at = NULL,
                finished_at = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND user_id = #{userId}
              AND status = 'FAILED'
            """)
    int resetFailed(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_content_analysis_task
            SET status = 'SUCCESS',
                progress_percent = 100,
                model_name = #{modelName},
                failure_code = NULL,
                failure_message = NULL,
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND status = 'RUNNING'
            """)
    int complete(
            @Param("taskId") Long taskId,
            @Param("modelName") String modelName,
            @Param("now") LocalDateTime now);
}
