package com.audioagent.processing.mapper;

import com.audioagent.processing.entity.AudioProcessingExecution;
import com.audioagent.processing.vo.ProcessingExecutionListVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AudioProcessingExecutionMapper
        extends BaseMapper<AudioProcessingExecution> {

    String COLUMNS = """
            id, user_id, task_id, audio_file_id, confirmation_id,
            source_plan_id, source_plan_revision, execution_status,
            accepted_step_count, executable_step_count, skipped_step_count,
            current_stage, progress_percent, result_file_id, retry_count,
            max_retry_count, failure_code, failure_message, started_at,
            finished_at, created_at, updated_at
            """;

    @Select("SELECT " + COLUMNS + " FROM audio_processing_execution "
            + "WHERE id = #{id}")
    AudioProcessingExecution selectExecutionById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM audio_processing_execution "
            + "WHERE confirmation_id = #{confirmationId}")
    AudioProcessingExecution selectByConfirmationId(
            @Param("confirmationId") Long confirmationId);

    @Select("""
            <script>
            SELECT e.id AS execution_id,
                   e.task_id,
                   e.audio_file_id,
                   f.original_name AS file_name,
                   e.execution_status,
                   e.current_stage,
                   e.progress_percent,
                   e.result_file_id,
                   e.failure_code,
                   e.started_at,
                   e.finished_at,
                   e.created_at
            FROM audio_processing_execution e
            LEFT JOIN audio_file f
              ON f.id = e.audio_file_id AND f.deleted = 0
            WHERE e.user_id = #{userId}
            <if test="status != null">
              AND e.execution_status = #{status}
            </if>
            ORDER BY e.created_at DESC
            </script>
            """)
    IPage<ProcessingExecutionListVO> selectExecutionPage(
            Page<ProcessingExecutionListVO> page,
            @Param("userId") Long userId,
            @Param("status") String status);

    @Insert("""
            INSERT IGNORE INTO audio_processing_execution (
                id, user_id, task_id, audio_file_id, confirmation_id,
                source_plan_id, source_plan_revision, execution_status,
                accepted_step_count, executable_step_count,
                skipped_step_count, current_stage, progress_percent,
                result_file_id, retry_count, max_retry_count,
                failure_code, failure_message, started_at, finished_at,
                created_at, updated_at
            ) VALUES (
                #{id}, #{userId}, #{taskId}, #{audioFileId},
                #{confirmationId}, #{sourcePlanId}, #{sourcePlanRevision},
                #{executionStatus}, #{acceptedStepCount},
                #{executableStepCount}, #{skippedStepCount},
                #{currentStage}, #{progressPercent}, #{resultFileId},
                #{retryCount}, #{maxRetryCount}, #{failureCode},
                #{failureMessage}, #{startedAt}, #{finishedAt},
                #{createdAt}, #{updatedAt}
            )
            """)
    int insertIgnore(AudioProcessingExecution execution);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'PROCESSING',
                current_stage = 'PREPARING',
                progress_percent = GREATEST(progress_percent, 5),
                failure_code = NULL, failure_message = NULL,
                started_at = COALESCE(started_at, #{now}),
                finished_at = NULL, updated_at = #{now}
            WHERE id = #{id}
              AND result_file_id IS NULL
              AND execution_status IN ('PENDING', 'QUEUED')
            """)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'QUEUED', updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'PENDING'
            """)
    int markQueued(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET current_stage = #{stage},
                progress_percent = GREATEST(progress_percent, #{progress}),
                updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'PROCESSING'
            """)
    int advance(@Param("id") Long id, @Param("stage") String stage,
                @Param("progress") int progress,
                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'QUEUED', retry_count = #{retryCount},
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'PROCESSING'
            """)
    int scheduleRetry(@Param("id") Long id,
                      @Param("retryCount") int retryCount,
                      @Param("failureCode") String failureCode,
                      @Param("failureMessage") String failureMessage,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = #{status}, failure_code = #{failureCode},
                failure_message = #{failureMessage}, finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'PROCESSING'
            """)
    int markFailed(@Param("id") Long id, @Param("status") String status,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'FAILED', failure_code = #{failureCode},
                failure_message = #{failureMessage}, finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{id} AND execution_status IN ('PENDING', 'QUEUED')
            """)
    int markDispatchFailed(@Param("id") Long id,
                           @Param("failureCode") String failureCode,
                           @Param("failureMessage") String failureMessage,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'FAILED', failure_code = #{failureCode},
                failure_message = #{failureMessage}, finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'QUEUED'
            """)
    int markQueuedFailure(@Param("id") Long id,
                          @Param("failureCode") String failureCode,
                          @Param("failureMessage") String failureMessage,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'SUCCESS', current_stage = 'COMPLETED',
                progress_percent = 100, result_file_id = #{resultFileId},
                failure_code = NULL, failure_message = NULL,
                finished_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND execution_status = 'PROCESSING'
              AND result_file_id IS NULL
            """)
    int complete(@Param("id") Long id,
                 @Param("resultFileId") Long resultFileId,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'PENDING', current_stage = NULL,
                progress_percent = 0, retry_count = 0,
                failure_code = NULL, failure_message = NULL,
                started_at = NULL, finished_at = NULL, updated_at = #{now}
            WHERE id = #{id} AND result_file_id IS NULL
              AND execution_status IN ('FAILED', 'DEAD_LETTER')
            """)
    int resetForManualRetry(@Param("id") Long id,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution
            SET execution_status = 'CANCELLED',
                current_stage = 'CANCELLED',
                failure_code = NULL,
                failure_message = NULL,
                finished_at = #{now}, updated_at = #{now}
            WHERE id = #{id}
              AND result_file_id IS NULL
              AND execution_status IN ('PENDING', 'QUEUED', 'PROCESSING')
            """)
    int cancel(@Param("id") Long id,
               @Param("now") LocalDateTime now);
}
