package com.audioagent.analysis.mapper;

import com.audioagent.analysis.entity.AudioProcessingStepConfirmation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AudioProcessingStepConfirmationMapper
        extends BaseMapper<AudioProcessingStepConfirmation> {

    @Insert("""
            <script>
            INSERT INTO audio_processing_step_confirmation (
                id, confirmation_id, source_step_id, decision,
                user_confirmed, parameter_overrides_json,
                effective_parameters_json, user_note,
                created_at, updated_at
            ) VALUES
            <foreach collection="steps" item="step" separator=",">
            (
                #{step.id}, #{step.confirmationId}, #{step.sourceStepId},
                #{step.decision}, #{step.userConfirmed},
                #{step.parameterOverridesJson},
                #{step.effectiveParametersJson}, #{step.userNote},
                #{step.createdAt}, #{step.updatedAt}
            )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("steps") List<AudioProcessingStepConfirmation> steps);

    @Select("""
            SELECT id, confirmation_id, source_step_id, decision,
                   user_confirmed, parameter_overrides_json,
                   effective_parameters_json, user_note,
                   created_at, updated_at
            FROM audio_processing_step_confirmation
            WHERE confirmation_id = #{confirmationId}
            """)
    List<AudioProcessingStepConfirmation> selectByConfirmationId(
            @Param("confirmationId") Long confirmationId);

    @Select("""
            SELECT id, confirmation_id, source_step_id, decision,
                   user_confirmed, parameter_overrides_json,
                   effective_parameters_json, user_note,
                   created_at, updated_at
            FROM audio_processing_step_confirmation
            WHERE id = #{stepConfirmationId}
              AND confirmation_id = #{confirmationId}
            """)
    AudioProcessingStepConfirmation selectOwnedStep(
            @Param("confirmationId") Long confirmationId,
            @Param("stepConfirmationId") Long stepConfirmationId);
}

