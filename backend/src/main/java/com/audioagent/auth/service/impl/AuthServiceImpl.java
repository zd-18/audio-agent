package com.audioagent.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.dto.ChangePasswordRequest;
import com.audioagent.auth.dto.LoginRequest;
import com.audioagent.auth.dto.RegisterRequest;
import com.audioagent.auth.entity.AppUser;
import com.audioagent.auth.entity.UserStatus;
import com.audioagent.auth.mapper.AppUserMapper;
import com.audioagent.auth.service.AuthService;
import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.auth.vo.LoginVO;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserVO register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        validatePassword(username, request.getPassword());
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName().trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(0);

        try {
            if (appUserMapper.insert(user) != 1 || user.getId() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "注册失败，请稍后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_ALREADY_EXISTS);
        }

        log.info("Application user registered, userId={}", user.getId());
        return CurrentUserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        AppUser user = findByUsername(normalizeUsername(request.getUsername()));
        if (user == null || !passwordMatches(request.getPassword(), user)) {
            throw new BusinessException(
                    ErrorCode.AUTH_USERNAME_OR_PASSWORD_INVALID);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(user.getLastLoginAt());
        if (appUserMapper.updateById(user) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "登录失败，请稍后重试");
        }

        StpUtil.login(user.getId());
        log.info("Application user logged in, userId={}", user.getId());
        return LoginVO.builder()
                .token(StpUtil.getTokenValue())
                .tokenName(StpUtil.getTokenName())
                .user(CurrentUserVO.from(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserVO getCurrentUser() {
        AppUser user = requireCurrentUser();
        return CurrentUserVO.from(user);
    }

    @Override
    public void logout() {
        Long userId = currentUserProvider.requireUserId();
        StpUtil.logout();
        log.info("Application user logged out, userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        AppUser user = requireCurrentUser();
        if (!passwordMatches(request.getOldPassword(), user)) {
            throw new BusinessException(ErrorCode.AUTH_OLD_PASSWORD_INVALID);
        }
        validatePassword(user.getUsername(), request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        if (appUserMapper.updateById(user) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "密码修改失败，请稍后重试");
        }

        StpUtil.logout(user.getId());
        log.info("Application user password changed, userId={}", user.getId());
    }

    private AppUser requireCurrentUser() {
        Long userId = currentUserProvider.requireUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            StpUtil.logout(userId);
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }
        return user;
    }

    private AppUser findByUsername(String username) {
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private boolean passwordMatches(String rawPassword, AppUser user) {
        try {
            return passwordEncoder.matches(rawPassword, user.getPasswordHash());
        } catch (RuntimeException e) {
            log.warn("Stored password hash is invalid, userId={}", user.getId());
            return false;
        }
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "用户名不能为空且不能超过 64 个字符");
        }
        return normalized;
    }

    private void validatePassword(String username, String password) {
        if (password == null || password.length() < 8
                || password.length() > 64 || password.isBlank()
                || password.equals(username)) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INVALID,
                    "密码长度须为 8～64 个字符，且不能全为空格或与用户名相同");
        }
    }
}
