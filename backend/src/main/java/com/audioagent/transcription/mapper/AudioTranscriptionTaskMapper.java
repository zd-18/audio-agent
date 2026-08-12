package com.audioagent.transcription.mapper;

import com.audioagent.transcription.entity.AudioTranscriptionTask;
import com.audioagent.transcription.vo.TranscriptionTaskVO;
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
public interface AudioTranscriptionTaskMapper
        extends BaseMapper<AudioTranscriptionTask> {

    @Select("""
            SELECT *
            FROM audio_transcription_task
            WHERE user_id = #{userId}
              AND audio_file_id = #{audioFileId}
              AND language = #{language}
              AND enable_speaker_diarization = #{diarization}
              AND status IN ('PENDING', 'RUNNING', 'SUCCESS')
            ORDER BY created_at DESC
            LIMIT 1
            """)
    AudioTranscriptionTask selectReusable(
            @Param("userId") Long userId,
            @Param("audioFileId") Long audioFileId,
            @Param("language") String language,
            @Param("diarization") boolean diarization);

    @Select("""
            <script>
            SELECT ranked.*
            FROM (
                SELECT t.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY t.audio_file_id
                           ORDER BY t.created_at DESC, t.id DESC
                       ) AS row_number_value
                FROM audio_transcription_task t
                WHERE t.user_id = #{userId}
                  AND t.audio_file_id IN
                  <foreach collection="audioFileIds" item="fileId"
                           open="(" separator="," close=")">
                    #{fileId}
                  </foreach>
            ) ranked
            WHERE ranked.row_number_value = 1
            </script>
            """)
    List<AudioTranscriptionTask> selectLatestForAudioFiles(
            @Param("userId") Long userId,
            @Param("audioFileIds") List<Long> audioFileIds);

    @Select("""
            <script>
            SELECT CAST(t.id AS CHAR) AS task_id,
                   CAST(t.audio_file_id AS CHAR) AS audio_file_id,
                   f.original_name AS audio_file_name,
                   t.status,
                   t.language,
                   t.enable_speaker_diarization,
                   t.progress_percent,
                   t.retry_count,
                   t.failure_code,
                   t.failure_message,
                   t.started_at,
                   t.finished_at,
                   t.created_at,
                   t.updated_at
            FROM audio_transcription_task t
            INNER JOIN audio_file f
              ON f.id = t.audio_file_id
             AND f.user_id = t.user_id
             AND f.deleted = 0
            WHERE t.user_id = #{userId}
            <if test="status != null">
              AND t.status = #{status}
            </if>
            <if test="audioFileId != null">
              AND t.audio_file_id = #{audioFileId}
            </if>
            ORDER BY t.created_at DESC
            </script>
            """)
    IPage<TranscriptionTaskVO> selectTaskPage(
            Page<TranscriptionTaskVO> page,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("audioFileId") Long audioFileId);

    @Select("""
            SELECT CAST(t.id AS CHAR) AS task_id,
                   CAST(t.audio_file_id AS CHAR) AS audio_file_id,
                   f.original_name AS audio_file_name,
                   t.status,
                   t.language,
                   t.enable_speaker_diarization,
                   t.progress_percent,
                   t.retry_count,
                   t.failure_code,
                   t.failure_message,
                   t.started_at,
                   t.finished_at,
                   t.created_at,
                   t.updated_at
            FROM audio_transcription_task t
            INNER JOIN audio_file f
              ON f.id = t.audio_file_id
             AND f.user_id = t.user_id
             AND f.deleted = 0
            WHERE t.id = #{taskId}
              AND t.user_id = #{userId}
            LIMIT 1
            """)
    TranscriptionTaskVO selectOwnedTaskView(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId);

    @Update("""
            UPDATE audio_transcription_task
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
            UPDATE audio_transcription_task
            SET progress_percent = #{progress}, updated_at = #{now}
            WHERE id = #{taskId} AND status = 'RUNNING'
            """)
    int updateProgress(@Param("taskId") Long taskId,
                       @Param("progress") int progress,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_transcription_task
            SET status = 'PENDING',
                progress_percent = 0,
                retry_count = #{retryCount},
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                finished_at = NULL,
                updated_at = #{now}
            WHERE id = #{taskId} AND status = 'RUNNING'
            """)
    int scheduleRetry(@Param("taskId") Long taskId,
                      @Param("retryCount") int retryCount,
                      @Param("failureCode") String failureCode,
                      @Param("failureMessage") String failureMessage,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_transcription_task
            SET status = 'FAILED',
                progress_percent = 0,
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{taskId} AND status != 'SUCCESS'
            """)
    int markFailed(@Param("taskId") Long taskId,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_transcription_task
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
    int resetFailed(@Param("userId") Long userId,
                    @Param("taskId") Long taskId,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_transcription_task
            SET status = 'SUCCESS',
                progress_percent = 100,
                provider = #{provider},
                model_name = #{modelName},
                failure_code = NULL,
                failure_message = NULL,
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{taskId} AND status = 'RUNNING'
            """)
    int complete(@Param("taskId") Long taskId,
                 @Param("provider") String provider,
                 @Param("modelName") String modelName,
                 @Param("now") LocalDateTime now);
}
