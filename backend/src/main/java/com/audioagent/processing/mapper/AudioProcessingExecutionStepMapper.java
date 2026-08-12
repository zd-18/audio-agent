package com.audioagent.processing.mapper;

import com.audioagent.processing.entity.AudioProcessingExecutionStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AudioProcessingExecutionStepMapper
        extends BaseMapper<AudioProcessingExecutionStep> {

    @Insert("""
            <script>
            INSERT INTO audio_processing_execution_step (
                id, execution_id, source_step_confirmation_id,
                source_processing_step_id, step_order, operation_type,
                execution_status, start_ms, end_ms,
                effective_parameters_json, skip_reason, failure_message,
                started_at, finished_at, created_at, updated_at
            ) VALUES
            <foreach collection="steps" item="step" separator=",">
            (
                #{step.id}, #{step.executionId},
                #{step.sourceStepConfirmationId},
                #{step.sourceProcessingStepId}, #{step.stepOrder},
                #{step.operationType}, #{step.executionStatus},
                #{step.startMs}, #{step.endMs},
                #{step.effectiveParametersJson}, #{step.skipReason},
                #{step.failureMessage}, #{step.startedAt},
                #{step.finishedAt}, #{step.createdAt}, #{step.updatedAt}
            )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("steps") List<AudioProcessingExecutionStep> steps);

    @Select("""
            SELECT id, execution_id, source_step_confirmation_id,
                   source_processing_step_id, step_order, operation_type,
                   execution_status, start_ms, end_ms,
                   effective_parameters_json, skip_reason, failure_message,
                   started_at, finished_at, created_at, updated_at
            FROM audio_processing_execution_step
            WHERE execution_id = #{executionId}
            ORDER BY step_order ASC, id ASC
            """)
    List<AudioProcessingExecutionStep> selectByExecutionId(
            @Param("executionId") Long executionId);

    @Update("""
            UPDATE audio_processing_execution_step
            SET execution_status = 'PROCESSING', started_at = #{now},
                finished_at = NULL, failure_message = NULL,
                updated_at = #{now}
            WHERE execution_id = #{executionId}
              AND execution_status = 'PENDING'
            """)
    int markExecutableProcessing(@Param("executionId") Long executionId,
                                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution_step
            SET execution_status = 'SUCCESS', finished_at = #{now},
                failure_message = NULL, updated_at = #{now}
            WHERE execution_id = #{executionId}
              AND execution_status = 'PROCESSING'
            """)
    int markProcessingSuccess(@Param("executionId") Long executionId,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution_step
            SET execution_status = 'FAILED', failure_message = #{message},
                finished_at = #{now}, updated_at = #{now}
            WHERE execution_id = #{executionId}
              AND execution_status = 'PROCESSING'
            """)
    int markProcessingFailed(@Param("executionId") Long executionId,
                             @Param("message") String message,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution_step
            SET execution_status = 'FAILED', failure_message = #{message},
                finished_at = #{now}, updated_at = #{now}
            WHERE execution_id = #{executionId}
              AND execution_status IN ('PENDING', 'PROCESSING')
            """)
    int markUnfinishedFailed(@Param("executionId") Long executionId,
                             @Param("message") String message,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE audio_processing_execution_step
            SET execution_status = 'PENDING', failure_message = NULL,
                started_at = NULL, finished_at = NULL, updated_at = #{now}
            WHERE execution_id = #{executionId}
              AND execution_status <> 'SKIPPED'
            """)
    int resetForRetry(@Param("executionId") Long executionId,
                      @Param("now") LocalDateTime now);
}
