package com.audioagent.setting.service.impl;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.entity.AppUser;
import com.audioagent.auth.mapper.AppUserMapper;
import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.setting.config.UserSettingDefaults;
import com.audioagent.setting.dto.UpdateUserProfileRequest;
import com.audioagent.setting.dto.UpdateUserSettingRequest;
import com.audioagent.setting.entity.UserSetting;
import com.audioagent.setting.enums.DenoiseStrength;
import com.audioagent.setting.enums.ProcessingStrategy;
import com.audioagent.setting.mapper.UserSettingMapper;
import com.audioagent.setting.model.UserProcessingPreferences;
import com.audioagent.setting.service.UserSettingService;
import com.audioagent.setting.vo.UserSettingVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingServiceImpl implements UserSettingService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES =
            Set.of(10, 20, 50);

    private final UserSettingMapper userSettingMapper;
    private final AppUserMapper appUserMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSettingVO getCurrent() {
        Long userId = currentUserProvider.requireUserId();
        return UserSettingVO.from(ensureSetting(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSettingVO updateCurrent(UpdateUserSettingRequest request) {
        Long userId = currentUserProvider.requireUserId();
        UserSetting setting = ensureSetting(userId);
        applyRequest(setting, request);
        setting.setUpdatedAt(LocalDateTime.now());
        try {
            userSettingMapper.updateByUserId(userId, setting);
            UserSetting saved = userSettingMapper.selectByUserId(userId);
            if (saved == null) {
                throw new BusinessException(
                        ErrorCode.USER_SETTING_UPDATE_FAILED);
            }
            return UserSettingVO.from(saved);
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("User setting update failed, userId={}", userId, e);
            throw new BusinessException(
                    ErrorCode.USER_SETTING_UPDATE_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserVO updateCurrentProfile(
            UpdateUserProfileRequest request) {
        Long userId = currentUserProvider.requireUserId();
        String displayName = normalizeDisplayName(request.getDisplayName());
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        user.setDisplayName(displayName);
        user.setUpdatedAt(LocalDateTime.now());
        try {
            appUserMapper.updateById(user);
            AppUser saved = appUserMapper.selectById(userId);
            if (saved == null) {
                throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
            }
            return CurrentUserVO.from(saved);
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("User profile update failed, userId={}", userId, e);
            throw new BusinessException(ErrorCode.USER_PROFILE_INVALID,
                    "显示名称保存失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProcessingPreferences getCurrentProcessingPreferences() {
        Long userId = currentUserProvider.requireUserId();
        return getProcessingPreferencesForOwner(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProcessingPreferences getProcessingPreferencesForOwner(
            Long userId) {
        if (userId == null || userId <= 0) {
            throw invalid("用户身份无效");
        }
        UserSetting setting = ensureSetting(userId);
        return new UserProcessingPreferences(
                setting.getDefaultDenoiseStrength(),
                setting.getProcessingStrategy(),
                Boolean.TRUE.equals(setting.getAutoLimitPeak()),
                Boolean.TRUE.equals(setting.getRequireStepConfirmation()));
    }

    private UserSetting ensureSetting(Long userId) {
        UserSetting existing = userSettingMapper.selectByUserId(userId);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        UserSetting defaults = UserSettingDefaults.create(
                IdWorker.getId(), userId, now);
        try {
            userSettingMapper.insertDefaults(defaults);
            UserSetting saved = userSettingMapper.selectByUserId(userId);
            if (saved == null) {
                throw new BusinessException(ErrorCode.USER_SETTING_NOT_FOUND,
                        "用户设置初始化失败，请稍后重试");
            }
            return saved;
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("User setting initialization failed, userId={}",
                    userId, e);
            throw new BusinessException(ErrorCode.USER_SETTING_UPDATE_FAILED,
                    "用户设置初始化失败，请稍后重试");
        }
    }

    private void applyRequest(UserSetting setting,
                              UpdateUserSettingRequest request) {
        if (request == null) {
            throw invalid("用户设置内容不能为空");
        }
        setting.setDefaultDenoiseStrength(parseDenoiseStrength(
                request.getDefaultDenoiseStrength()));
        setting.setProcessingStrategy(parseProcessingStrategy(
                request.getProcessingStrategy()));
        setting.setAutoLimitPeak(request.getAutoLimitPeak());
        setting.setRequireStepConfirmation(
                request.getRequireStepConfirmation());
        setting.setPreservePlaybackPosition(
                request.getPreservePlaybackPosition());
        Integer contextSeconds = request.getIssueContextSeconds();
        if (contextSeconds == null || contextSeconds < 0
                || contextSeconds > 10) {
            throw invalid("问题片段上下文秒数必须在 0～10 之间");
        }
        setting.setIssueContextSeconds(contextSeconds);
        BigDecimal volume = request.getDefaultPlaybackVolume();
        if (volume == null || volume.compareTo(BigDecimal.ZERO) < 0
                || volume.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("默认播放音量必须在 0～1 之间");
        }
        setting.setDefaultPlaybackVolume(
                volume);
        if (!ALLOWED_PAGE_SIZES.contains(request.getDefaultPageSize())) {
            throw invalid("默认分页数量只能选择 10、20 或 50");
        }
        setting.setDefaultPageSize(request.getDefaultPageSize());
        setting.setNotifyOnTaskComplete(
                request.getNotifyOnTaskComplete());
        setting.setAutoOpenResultPage(request.getAutoOpenResultPage());
    }

    private DenoiseStrength parseDenoiseStrength(String value) {
        try {
            return DenoiseStrength.valueOf(value.trim());
        } catch (RuntimeException e) {
            throw invalid("默认降噪强度只能选择轻度或中度");
        }
    }

    private ProcessingStrategy parseProcessingStrategy(String value) {
        try {
            return ProcessingStrategy.valueOf(value.trim());
        } catch (RuntimeException e) {
            throw invalid("处理策略只能选择保守或均衡");
        }
    }

    private String normalizeDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_PROFILE_INVALID,
                    "显示名称不能为空或全为空格");
        }
        if (normalized.length() > 50) {
            throw new BusinessException(ErrorCode.USER_PROFILE_INVALID,
                    "显示名称不能超过 50 个字符");
        }
        return normalized;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.USER_SETTING_INVALID,
                message);
    }
}
