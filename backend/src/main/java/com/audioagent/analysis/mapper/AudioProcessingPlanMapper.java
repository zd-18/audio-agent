package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioProcessingPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioProcessingPlanMapper
        extends BaseMapper<AudioProcessingPlan> {

    @Select("""
            SELECT id, task_id, audio_file_id, plan_version, plan_revision, plan_status,
                   summary, step_count, estimated_output_duration_ms,
                   plan_json, created_at, updated_at
            FROM audio_processing_plan
            WHERE task_id = #{taskId}
            """)
    AudioProcessingPlan selectByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, task_id, audio_file_id, plan_version, plan_revision, plan_status,
                   summary, step_count, estimated_output_duration_ms,
                   plan_json, created_at, updated_at
            FROM audio_processing_plan
            WHERE task_id = #{taskId}
            FOR UPDATE
            """)
    AudioProcessingPlan selectByTaskIdForUpdate(
            @Param("taskId") Long taskId);

    @Insert("""
            INSERT INTO audio_processing_plan (
                id, task_id, audio_file_id, plan_version, plan_revision, plan_status,
                summary, step_count, estimated_output_duration_ms,
                plan_json, created_at, updated_at
            ) VALUES (
                #{id}, #{taskId}, #{audioFileId}, #{planVersion},
                #{planRevision}, #{planStatus}, #{summary}, #{stepCount},
                #{estimatedOutputDurationMs}, #{planJson},
                #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                audio_file_id = VALUES(audio_file_id),
                plan_version = VALUES(plan_version),
                plan_revision = VALUES(plan_revision),
                plan_status = VALUES(plan_status),
                summary = VALUES(summary),
                step_count = VALUES(step_count),
                estimated_output_duration_ms =
                    VALUES(estimated_output_duration_ms),
                plan_json = VALUES(plan_json),
                updated_at = VALUES(updated_at)
            """)
    int upsert(AudioProcessingPlan plan);
}
