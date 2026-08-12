package com.audioagent.auth.config;

import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaTokenSessionLifecycleTest {

    @Test
    void logoutMakesIssuedTokenInvalid() {
        StpLogic logic = new StpLogic(
                "auth-lifecycle-" + UUID.randomUUID());
        String token = logic.createLoginSession(901L);

        assertEquals("901", String.valueOf(logic.getLoginIdByToken(token)));

        logic.logoutByTokenValue(token);

        assertNull(logic.getLoginIdByToken(token));
    }
}
