package com.audioagent.setting.controller;

import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.common.api.ApiResponse;
import com.audioagent.setting.dto.UpdateUserProfileRequest;
import com.audioagent.setting.dto.UpdateUserSettingRequest;
import com.audioagent.setting.service.UserSettingService;
import com.audioagent.setting.vo.UserSettingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingController {

    private final UserSettingService userSettingService;

    @GetMapping("/me")
    public ApiResponse<UserSettingVO> getCurrent() {
        return ApiResponse.success(userSettingService.getCurrent());
    }

    @PutMapping("/me")
    public ApiResponse<UserSettingVO> updateCurrent(
            @Valid @RequestBody UpdateUserSettingRequest request) {
        return ApiResponse.success(
                userSettingService.updateCurrent(request));
    }

    @PutMapping("/profile")
    public ApiResponse<CurrentUserVO> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(
                userSettingService.updateCurrentProfile(request));
    }
}
