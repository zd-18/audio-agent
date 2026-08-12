package com.audioagent.setting.mapper;

import com.audioagent.setting.entity.UserSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserSettingMapper extends BaseMapper<UserSetting> {

    @Select("""
            SELECT id, user_id, default_denoise_strength,
                   processing_strategy, auto_limit_peak,
                   require_step_confirmation, preserve_playback_position,
                   issue_context_seconds, default_playback_volume,
                   default_page_size, notify_on_task_complete,
                   auto_open_result_page, created_at, updated_at
            FROM user_setting
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    UserSetting selectByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO user_setting (
                id, user_id, default_denoise_strength,
                processing_strategy, auto_limit_peak,
                require_step_confirmation, preserve_playback_position,
                issue_context_seconds, default_playback_volume,
                default_page_size, notify_on_task_complete,
                auto_open_result_page, created_at, updated_at
            ) VALUES (
                #{id}, #{userId}, #{defaultDenoiseStrength},
                #{processingStrategy}, #{autoLimitPeak},
                #{requireStepConfirmation}, #{preservePlaybackPosition},
                #{issueContextSeconds}, #{defaultPlaybackVolume},
                #{defaultPageSize}, #{notifyOnTaskComplete},
                #{autoOpenResultPage}, #{createdAt}, #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """)
    int insertDefaults(UserSetting setting);

    @Update("""
            UPDATE user_setting
            SET default_denoise_strength = #{setting.defaultDenoiseStrength},
                processing_strategy = #{setting.processingStrategy},
                auto_limit_peak = #{setting.autoLimitPeak},
                require_step_confirmation =
                    #{setting.requireStepConfirmation},
                preserve_playback_position =
                    #{setting.preservePlaybackPosition},
                issue_context_seconds = #{setting.issueContextSeconds},
                default_playback_volume =
                    #{setting.defaultPlaybackVolume},
                default_page_size = #{setting.defaultPageSize},
                notify_on_task_complete = #{setting.notifyOnTaskComplete},
                auto_open_result_page = #{setting.autoOpenResultPage},
                updated_at = #{setting.updatedAt}
            WHERE user_id = #{userId}
            """)
    int updateByUserId(@Param("userId") Long userId,
                       @Param("setting") UserSetting setting);
}
