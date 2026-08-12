package com.audioagent.setting.service;

import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.setting.dto.UpdateUserProfileRequest;
import com.audioagent.setting.dto.UpdateUserSettingRequest;
import com.audioagent.setting.model.UserProcessingPreferences;
import com.audioagent.setting.vo.UserSettingVO;

public interface UserSettingService {

    UserSettingVO getCurrent();

    UserSettingVO updateCurrent(UpdateUserSettingRequest request);

    CurrentUserVO updateCurrentProfile(UpdateUserProfileRequest request);

    UserProcessingPreferences getCurrentProcessingPreferences();

    UserProcessingPreferences getProcessingPreferencesForOwner(Long userId);
}
