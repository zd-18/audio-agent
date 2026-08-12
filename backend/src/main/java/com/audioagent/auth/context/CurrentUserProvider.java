package com.audioagent.auth.context;

import cn.dev33.satoken.stp.StpUtil;
import com.audioagent.auth.entity.AppUser;
import com.audioagent.auth.entity.UserStatus;
import com.audioagent.auth.mapper.AppUserMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final AppUserMapper appUserMapper;

    public Long requireUserId() {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            StpUtil.logout(userId);
            throw new BusinessException(ErrorCode.AUTH_USER_DISABLED);
        }
        return userId;
    }
}
