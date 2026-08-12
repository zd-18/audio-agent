package com.audioagent.setting.service;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.entity.AppUser;
import com.audioagent.auth.entity.UserStatus;
import com.audioagent.auth.mapper.AppUserMapper;
import com.audioagent.auth.service.impl.AuthServiceImpl;
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
import com.audioagent.setting.service.impl.UserSettingServiceImpl;
import com.audioagent.setting.vo.UserSettingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingServiceImplTest {

    private static final long USER_A = 2_079_466_165_271_707_649L;
    private static final long USER_B = 2_079_466_165_271_707_650L;

    @Mock
    private UserSettingMapper userSettingMapper;
    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserSettingServiceImpl(userSettingMapper,
                appUserMapper, currentUserProvider);
    }

    @Test
    void firstReadCreatesDefaultsAndSecondReadIsIdempotent() {
        UserSetting defaults = setting(USER_A);
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        when(userSettingMapper.selectByUserId(USER_A))
                .thenReturn(null, defaults, defaults);

        UserSettingVO first = service.getCurrent();
        UserSettingVO second = service.getCurrent();

        assertEquals(DenoiseStrength.LIGHT,
                first.getDefaultDenoiseStrength());
        assertEquals(20, first.getDefaultPageSize());
        assertEquals(first.getDefaultPageSize(), second.getDefaultPageSize());
        verify(userSettingMapper, times(1))
                .insertDefaults(any(UserSetting.class));
    }

    @Test
    void usersReadIndependentSettingRows() {
        UserSetting first = setting(USER_A);
        UserSetting second = setting(USER_B);
        second.setDefaultPageSize(50);
        when(currentUserProvider.requireUserId())
                .thenReturn(USER_A, USER_B);
        when(userSettingMapper.selectByUserId(USER_A)).thenReturn(first);
        when(userSettingMapper.selectByUserId(USER_B)).thenReturn(second);

        assertEquals(20, service.getCurrent().getDefaultPageSize());
        assertEquals(50, service.getCurrent().getDefaultPageSize());
        verify(userSettingMapper, never())
                .insertDefaults(any(UserSetting.class));
    }

    @Test
    void updatePersistsValidatedCurrentUserSettings() {
        UserSetting current = setting(USER_A);
        UserSetting saved = setting(USER_A);
        saved.setDefaultDenoiseStrength(DenoiseStrength.MEDIUM);
        saved.setProcessingStrategy(ProcessingStrategy.BALANCED);
        saved.setDefaultPlaybackVolume(new BigDecimal("0.65"));
        saved.setDefaultPageSize(50);
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        when(userSettingMapper.selectByUserId(USER_A))
                .thenReturn(current, saved);
        when(userSettingMapper.updateByUserId(eq(USER_A), any()))
                .thenReturn(1);

        UserSettingVO result = service.updateCurrent(validRequest());

        assertEquals(DenoiseStrength.MEDIUM,
                result.getDefaultDenoiseStrength());
        assertEquals(ProcessingStrategy.BALANCED,
                result.getProcessingStrategy());
        assertEquals(50, result.getDefaultPageSize());
        ArgumentCaptor<UserSetting> captor =
                ArgumentCaptor.forClass(UserSetting.class);
        verify(userSettingMapper).updateByUserId(eq(USER_A),
                captor.capture());
        assertEquals(new BigDecimal("0.65"),
                captor.getValue().getDefaultPlaybackVolume());
    }

    @Test
    void repeatedSaveWithNoChangedRowsStillSucceeds() {
        UserSetting current = setting(USER_A);
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        when(userSettingMapper.selectByUserId(USER_A))
                .thenReturn(current, current);
        when(userSettingMapper.updateByUserId(eq(USER_A), any()))
                .thenReturn(0);

        UserSettingVO result = service.updateCurrent(defaultRequest());

        assertEquals(20, result.getDefaultPageSize());
    }

    @Test
    void invalidDenoiseStrengthIsRejectedBeforeUpdate() {
        assertInvalid(request -> request.setDefaultDenoiseStrength("STRONG"));
    }

    @Test
    void invalidVolumeIsRejectedBeforeUpdate() {
        assertInvalid(request -> request.setDefaultPlaybackVolume(
                new BigDecimal("1.01")));
    }

    @Test
    void invalidIssueContextIsRejectedBeforeUpdate() {
        assertInvalid(request -> request.setIssueContextSeconds(11));
    }

    @Test
    void invalidPageSizeIsRejectedBeforeUpdate() {
        assertInvalid(request -> request.setDefaultPageSize(25));
    }

    @Test
    void profileUpdateIsImmediatelyVisibleToCurrentUserEndpoint() {
        AppUser user = user("旧名称");
        AppUser saved = user("张丹");
        when(currentUserProvider.requireUserId()).thenReturn(USER_A, USER_A);
        when(appUserMapper.selectById(USER_A)).thenReturn(user, saved, saved);
        when(appUserMapper.updateById(user)).thenReturn(1);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setDisplayName("  张丹  ");

        CurrentUserVO result = service.updateCurrentProfile(request);
        CurrentUserVO currentUser = new AuthServiceImpl(appUserMapper,
                passwordEncoder, currentUserProvider).getCurrentUser();

        assertEquals("张丹", result.getDisplayName());
        assertEquals("张丹", currentUser.getDisplayName());
        assertEquals("张丹", user.getDisplayName());
    }

    @Test
    void blankProfileNameIsRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setDisplayName("   ");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateCurrentProfile(request));

        assertEquals(ErrorCode.USER_PROFILE_INVALID.getCode(),
                error.getCode());
        verify(appUserMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void processingPreferencesComeFromCurrentUsersPersistedRow() {
        UserSetting current = setting(USER_A);
        current.setDefaultDenoiseStrength(DenoiseStrength.MEDIUM);
        current.setProcessingStrategy(ProcessingStrategy.BALANCED);
        current.setAutoLimitPeak(false);
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        when(userSettingMapper.selectByUserId(USER_A)).thenReturn(current);

        UserProcessingPreferences preferences =
                service.getCurrentProcessingPreferences();

        assertEquals(DenoiseStrength.MEDIUM,
                preferences.defaultDenoiseStrength());
        assertEquals(ProcessingStrategy.BALANCED,
                preferences.processingStrategy());
        assertFalse(preferences.autoLimitPeak());
    }

    private void assertInvalid(Consumer<UpdateUserSettingRequest> change) {
        when(currentUserProvider.requireUserId()).thenReturn(USER_A);
        when(userSettingMapper.selectByUserId(USER_A))
                .thenReturn(setting(USER_A));
        UpdateUserSettingRequest request = defaultRequest();
        change.accept(request);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateCurrent(request));

        assertEquals(ErrorCode.USER_SETTING_INVALID.getCode(),
                error.getCode());
        verify(userSettingMapper, never()).updateByUserId(any(), any());
    }

    private UpdateUserSettingRequest validRequest() {
        UpdateUserSettingRequest request = defaultRequest();
        request.setDefaultDenoiseStrength("MEDIUM");
        request.setProcessingStrategy("BALANCED");
        request.setDefaultPlaybackVolume(new BigDecimal("0.65"));
        request.setDefaultPageSize(50);
        return request;
    }

    private UpdateUserSettingRequest defaultRequest() {
        UpdateUserSettingRequest request = new UpdateUserSettingRequest();
        request.setDefaultDenoiseStrength("LIGHT");
        request.setProcessingStrategy("CONSERVATIVE");
        request.setAutoLimitPeak(true);
        request.setRequireStepConfirmation(true);
        request.setPreservePlaybackPosition(true);
        request.setIssueContextSeconds(2);
        request.setDefaultPlaybackVolume(new BigDecimal("0.80"));
        request.setDefaultPageSize(20);
        request.setNotifyOnTaskComplete(true);
        request.setAutoOpenResultPage(false);
        return request;
    }

    private UserSetting setting(long userId) {
        return UserSettingDefaults.create(userId + 100, userId,
                LocalDateTime.now());
    }

    private AppUser user(String displayName) {
        AppUser user = new AppUser();
        user.setId(USER_A);
        user.setUsername("zhangdan");
        user.setPasswordHash("$2a$12$hash");
        user.setDisplayName(displayName);
        user.setStatus(UserStatus.ACTIVE);
        user.setDeleted(0);
        return user;
    }
}
