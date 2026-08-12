package com.audioagent.auth.vo;

import com.audioagent.auth.entity.AppUser;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentUserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private final Long id;
    private final String username;
    private final String displayName;
    private final String avatarUrl;

    public static CurrentUserVO from(AppUser user) {
        return CurrentUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
