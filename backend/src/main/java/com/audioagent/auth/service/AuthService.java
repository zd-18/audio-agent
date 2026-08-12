package com.audioagent.auth.service;

import com.audioagent.auth.dto.ChangePasswordRequest;
import com.audioagent.auth.dto.LoginRequest;
import com.audioagent.auth.dto.RegisterRequest;
import com.audioagent.auth.vo.CurrentUserVO;
import com.audioagent.auth.vo.LoginVO;

public interface AuthService {
    CurrentUserVO register(RegisterRequest request);
    LoginVO login(LoginRequest request);
    CurrentUserVO getCurrentUser();
    void logout();
    void changePassword(ChangePasswordRequest request);
}
