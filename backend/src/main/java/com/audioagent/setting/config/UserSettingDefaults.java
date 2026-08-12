package com.audioagent.setting.config;

import com.audioagent.setting.entity.UserSetting;
import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;
import com.audioagent.setting.model.UserProcessingPreferences;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class UserSettingDefaults {

    public static final DenoiseStrength DEFAULT_DENOISE_STRENGTH =
            DenoiseStrength.LIGHT;
    public static final ProcessingStrategy PROCESSING_STRATEGY =
            ProcessingStrategy.CONSERVATIVE;
    public static final boolean AUTO_LIMIT_PEAK = true;
    public static final boolean REQUIRE_STEP_CONFIRMATION = true;
    public static final boolean PRESERVE_PLAYBACK_POSITION = true;
    public static final int ISSUE_CONTEXT_SECONDS = 2;
    public static final BigDecimal DEFAULT_PLAYBACK_VOLUME =
            new BigDecimal("0.80");
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final boolean NOTIFY_ON_TASK_COMPLETE = true;
    public static final boolean AUTO_OPEN_RESULT_PAGE = false;

    private UserSettingDefaults() {
    }

    public static UserSetting create(Long id, Long userId,
                                     LocalDateTime now) {
        UserSetting setting = new UserSetting();
        setting.setId(id);
        setting.setUserId(userId);
        setting.setDefaultDenoiseStrength(DEFAULT_DENOISE_STRENGTH);
        setting.setProcessingStrategy(PROCESSING_STRATEGY);
        setting.setAutoLimitPeak(AUTO_LIMIT_PEAK);
        setting.setRequireStepConfirmation(REQUIRE_STEP_CONFIRMATION);
        setting.setPreservePlaybackPosition(PRESERVE_PLAYBACK_POSITION);
        setting.setIssueContextSeconds(ISSUE_CONTEXT_SECONDS);
        setting.setDefaultPlaybackVolume(DEFAULT_PLAYBACK_VOLUME);
        setting.setDefaultPageSize(DEFAULT_PAGE_SIZE);
        setting.setNotifyOnTaskComplete(NOTIFY_ON_TASK_COMPLETE);
        setting.setAutoOpenResultPage(AUTO_OPEN_RESULT_PAGE);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    public static UserProcessingPreferences processingPreferences() {
        return new UserProcessingPreferences(DEFAULT_DENOISE_STRENGTH,
                PROCESSING_STRATEGY, AUTO_LIMIT_PEAK,
                REQUIRE_STEP_CONFIRMATION);
    }
}
