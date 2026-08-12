package com.audioagent.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.dto.ChangePasswordRequest;
import com.audioagent.auth.dto.LoginRequest;
import com.audioagent.auth.dto.RegisterRequest;
import com.audioagent.auth.entity.AppUser;
import com.audioagent.auth.entity.UserStatus;
import com.audioagent.auth.mapper.AppUserMapper;
import com.audioagent.auth.service.impl.AuthServiceImpl;
import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.auth.vo.LoginVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final long USER_ID = 2079466165271707649L;

    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @InjectMocks
    private AuthServiceImpl service;

    @Test
    void registerCreatesActiveUserWithHashedPassword() {
        when(appUserMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secure-pass")).thenReturn("$2a$12$hashed");
        when(appUserMapper.insert(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(USER_ID);
            return 1;
        });

        CurrentUserVO result = service.register(registerRequest());

        assertEquals(USER_ID, result.getId());
        assertEquals("zhangdan", result.getUsername());
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserMapper).insert(captor.capture());
        assertEquals("$2a$12$hashed", captor.getValue().getPasswordHash());
        assertNotEquals("secure-pass", captor.getValue().getPasswordHash());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void duplicateUsernameRegistrationFailsClearly() {
        when(appUserMapper.selectOne(any())).thenReturn(activeUser());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register(registerRequest()));

        assertEquals(ErrorCode.AUTH_USERNAME_ALREADY_EXISTS.getCode(),
                error.getCode());
        verify(appUserMapper, never()).insert(any(AppUser.class));
    }

    @Test
    void correctPasswordLoginReturnsTokenAndUpdatesLastLogin() {
        AppUser user = activeUser();
        when(appUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secure-pass", user.getPasswordHash()))
                .thenReturn(true);
        when(appUserMapper.updateById(user)).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("token-value");
            stp.when(StpUtil::getTokenName).thenReturn("Authorization");

            LoginVO result = service.login(loginRequest("secure-pass"));

            assertEquals("token-value", result.getToken());
            assertEquals("Authorization", result.getTokenName());
            assertEquals(USER_ID, result.getUser().getId());
            assertNotNull(user.getLastLoginAt());
            stp.verify(() -> StpUtil.login(USER_ID));
        }
    }

    @Test
    void wrongPasswordUsesUniformLoginError() {
        AppUser user = activeUser();
        when(appUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong-pass", user.getPasswordHash()))
                .thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("wrong-pass")));

        assertEquals(ErrorCode.AUTH_USERNAME_OR_PASSWORD_INVALID.getCode(),
                error.getCode());
    }

    @Test
    void missingUserUsesSameUniformLoginError() {
        when(appUserMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("wrong-pass")));

        assertEquals(ErrorCode.AUTH_USERNAME_OR_PASSWORD_INVALID.getCode(),
                error.getCode());
    }

    @Test
    void disabledUserCannotLogin() {
        AppUser user = activeUser();
        user.setStatus(UserStatus.DISABLED);
        when(appUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secure-pass", user.getPasswordHash()))
                .thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(loginRequest("secure-pass")));

        assertEquals(ErrorCode.AUTH_USER_DISABLED.getCode(), error.getCode());
    }

    @Test
    void currentUserComesFromLoginState() {
        when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
        when(appUserMapper.selectById(USER_ID)).thenReturn(activeUser());

        CurrentUserVO result = service.getCurrentUser();

        assertEquals(USER_ID, result.getId());
        assertEquals("张丹", result.getDisplayName());
    }

    @Test
    void logoutInvalidatesCurrentSaTokenSession() {
        when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.logout();
            stp.verify(StpUtil::logout);
        }
    }

    @Test
    void changePasswordChecksOldPasswordAndInvalidatesAllSessions() {
        AppUser user = activeUser();
        when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
        when(appUserMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("secure-pass", user.getPasswordHash()))
                .thenReturn(true);
        when(passwordEncoder.encode("new-secure-pass"))
                .thenReturn("$2a$12$new-hash");
        when(appUserMapper.updateById(user)).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            service.changePassword(changePasswordRequest(
                    "secure-pass", "new-secure-pass"));

            assertEquals("$2a$12$new-hash", user.getPasswordHash());
            stp.verify(() -> StpUtil.logout(USER_ID));
        }
    }

    @Test
    void wrongOldPasswordDoesNotChangeStoredHash() {
        AppUser user = activeUser();
        when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
        when(appUserMapper.selectById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("wrong-pass", user.getPasswordHash()))
                .thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(changePasswordRequest(
                        "wrong-pass", "new-secure-pass")));

        assertEquals(ErrorCode.AUTH_OLD_PASSWORD_INVALID.getCode(),
                error.getCode());
        verify(appUserMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void currentUserLongIdSerializesAsString() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                CurrentUserVO.from(activeUser()));

        assertTrue(json.contains("\"id\":\"" + USER_ID + "\""));
        assertFalse(json.contains("passwordHash"));
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("zhangdan");
        request.setPassword("secure-pass");
        request.setDisplayName("张丹");
        return request;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("zhangdan");
        request.setPassword(password);
        return request;
    }

    private ChangePasswordRequest changePasswordRequest(
            String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private AppUser activeUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setUsername("zhangdan");
        user.setPasswordHash("$2a$12$stored-hash");
        user.setDisplayName("张丹");
        user.setStatus(UserStatus.ACTIVE);
        user.setDeleted(0);
        return user;
    }
}
