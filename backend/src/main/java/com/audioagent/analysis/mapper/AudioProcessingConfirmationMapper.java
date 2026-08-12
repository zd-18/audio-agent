package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioProcessingConfirmation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AudioProcessingConfirmationMapper
        extends BaseMapper<AudioProcessingConfirmation> {

    @Select("""
            SELECT id, task_id, audio_file_id, plan_id,
                   source_plan_revision, confirmation_status,
                   accepted_step_count, rejected_step_count,
                   pending_step_count, confirmation_json, confirmed_at,
                   created_at, updated_at
            FROM audio_processing_confirmation
            WHERE plan_id = #{planId}
              AND source_plan_revision = #{planRevision}
            """)
    AudioProcessingConfirmation selectByPlanRevision(
            @Param("planId") Long planId,
            @Param("planRevision") Integer planRevision);

    @Select("""
            SELECT id, task_id, audio_file_id, plan_id,
                   source_plan_revision, confirmation_status,
                   accepted_step_count, rejected_step_count,
                   pending_step_count, confirmation_json, confirmed_at,
                   created_at, updated_at
            FROM audio_processing_confirmation
            WHERE id = #{id}
            FOR UPDATE
            """)
    AudioProcessingConfirmation selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id, task_id, audio_file_id, plan_id,
                   source_plan_revision, confirmation_status,
                   accepted_step_count, rejected_step_count,
                   pending_step_count, confirmation_json, confirmed_at,
                   created_at, updated_at
            FROM audio_processing_confirmation
            WHERE task_id = #{taskId}
              AND confirmation_status = 'CONFIRMED'
            ORDER BY confirmed_at DESC, id DESC
            LIMIT 1
            """)
    AudioProcessingConfirmation selectLatestConfirmedByTaskId(
            @Param("taskId") Long taskId);

    @Insert("""
            INSERT IGNORE INTO audio_processing_confirmation (
                id, task_id, audio_file_id, plan_id,
                source_plan_revision, confirmation_status,
                accepted_step_count, rejected_step_count,
                pending_step_count, confirmation_json, confirmed_at,
                created_at, updated_at
            ) VALUES (
                #{id}, #{taskId}, #{audioFileId}, #{planId},
                #{sourcePlanRevision}, #{confirmationStatus},
                #{acceptedStepCount}, #{rejectedStepCount},
                #{pendingStepCount}, #{confirmationJson}, #{confirmedAt},
                #{createdAt}, #{updatedAt}
            )
            """)
    int insertIgnore(AudioProcessingConfirmation confirmation);

    @Update("""
            UPDATE audio_processing_confirmation
            SET accepted_step_count = #{accepted},
                rejected_step_count = #{rejected},
                pending_step_count = #{pending},
                updated_at = #{updatedAt}
            WHERE id = #{id} AND confirmation_status = 'DRAFT'
            """)
    int updateCounts(@Param("id") Long id,
                     @Param("accepted") int accepted,
                     @Param("rejected") int rejected,
                     @Param("pending") int pending,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE audio_processing_confirmation
            SET confirmation_status = 'STALE', updated_at = #{updatedAt}
            WHERE plan_id = #{planId}
              AND source_plan_revision <> #{currentRevision}
              AND confirmation_status = 'DRAFT'
            """)
    int markOldDraftsStale(@Param("planId") Long planId,
                           @Param("currentRevision") Integer currentRevision,
                           @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE audio_processing_confirmation
            SET confirmation_status = 'CONFIRMED',
                confirmation_json = #{confirmationJson},
                confirmed_at = #{confirmedAt}, updated_at = #{confirmedAt}
            WHERE id = #{id} AND confirmation_status = 'DRAFT'
            """)
    int confirmDraft(@Param("id") Long id,
                     @Param("confirmationJson") String confirmationJson,
                     @Param("confirmedAt") LocalDateTime confirmedAt);

    @Update("""
            UPDATE audio_processing_confirmation
            SET confirmation_status = 'CANCELLED', updated_at = #{updatedAt}
            WHERE id = #{id} AND confirmation_status = 'DRAFT'
            """)
    int cancelDraft(@Param("id") Long id,
                    @Param("updatedAt") LocalDateTime updatedAt);
}
