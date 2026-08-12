package com.audioagent.auth.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.auth.controller.AuthController;
import com.audioagent.auth.service.AuthService;
import com.audioagent.common.exception.GlobalExceptionHandler;
import com.audioagent.file.controller.AudioFileController;
import com.audioagent.file.service.AudioFileService;
import com.audioagent.infrastructure.minio.MinioStorageService;
import com.audioagent.setting.controller.UserSettingController;
import com.audioagent.setting.service.UserSettingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AudioFileController.class,
        UserSettingController.class})
@Import({SaTokenConfig.class, GlobalExceptionHandler.class})
class SaTokenProtectionTest {

    private static SaTokenContext previousContext;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private AudioFileService audioFileService;
    @MockitoBean
    private MinioStorageService minioStorageService;
    @MockitoBean
    private CurrentUserProvider currentUserProvider;
    @MockitoBean
    private UserSettingService userSettingService;

    @BeforeAll
    static void installServletContextAdapter() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(
                new SaTokenContextForSpringInJakartaServlet());
    }

    @AfterAll
    static void restoreContextAdapter() {
        SaManager.setSaTokenContext(previousContext);
    }

    @Test
    void unauthenticatedBusinessApiIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40501));
    }

    @Test
    void unauthenticatedSettingsApiIsRejected() throws Exception {
        mockMvc.perform(get("/api/settings/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40501));
    }

    @Test
    void loginRouteRemainsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
