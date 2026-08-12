package com.audioagent.auth.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginVO {
    private final String token;
    private final String tokenName;
    private final CurrentUserVO user;
}
