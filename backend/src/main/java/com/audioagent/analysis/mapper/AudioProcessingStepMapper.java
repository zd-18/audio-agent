package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioProcessingStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AudioProcessingStepMapper
        extends BaseMapper<AudioProcessingStep> {

    @Delete("DELETE FROM audio_processing_step WHERE plan_id = #{planId}")
    int deleteByPlanId(@Param("planId") Long planId);

    @Select("""
            SELECT id, plan_id, step_order, operation_type, title,
                   description, source_issue_id, start_ms, end_ms,
                   priority, risk_level, requires_confirmation,
                   parameters_json, reason, created_at, updated_at
            FROM audio_processing_step
            WHERE plan_id = #{planId}
            ORDER BY step_order ASC
            """)
    List<AudioProcessingStep> selectByPlanId(
            @Param("planId") Long planId);

    @Insert("""
            <script>
            INSERT INTO audio_processing_step (
                id, plan_id, step_order, operation_type, title,
                description, source_issue_id, start_ms, end_ms,
                priority, risk_level, requires_confirmation,
                parameters_json, reason, created_at, updated_at
            ) VALUES
            <foreach collection="steps" item="step" separator=",">
            (
                #{step.id}, #{step.planId}, #{step.stepOrder},
                #{step.operationType}, #{step.title}, #{step.description},
                #{step.sourceIssueId}, #{step.startMs}, #{step.endMs},
                #{step.priority}, #{step.riskLevel},
                #{step.requiresConfirmation}, #{step.parametersJson},
                #{step.reason}, #{step.createdAt}, #{step.updatedAt}
            )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("steps") List<AudioProcessingStep> steps);
}
