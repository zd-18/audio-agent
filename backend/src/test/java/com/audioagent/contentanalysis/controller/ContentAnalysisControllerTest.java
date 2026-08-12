package com.audioagent.contentanalysis.controller;

import com.audioagent.auth.context.CurrentUserProvider;
import com.audioagent.contentanalysis.model.AnalysisType;
import com.audioagent.contentanalysis.service.ContentAnalysisService;
import com.audioagent.contentanalysis.vo.ContentAnalysisTaskVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentAnalysisControllerTest {

    @Test
    void failedTaskDetailReturnsHttp200() throws Exception {
        ContentAnalysisService service =
                mock(ContentAnalysisService.class);
        CurrentUserProvider currentUserProvider =
                mock(CurrentUserProvider.class);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(service.get(7L, "91")).thenReturn(
                ContentAnalysisTaskVO.builder()
                        .taskId("91")
                        .transcriptId("81")
                        .status("FAILED")
                        .analysisTypes(List.of(AnalysisType.SUMMARY))
                        .progressPercent(0)
                        .retryCount(0)
                        .failureCode(
                                "AI_RESULT_PERSISTENCE_FAILED")
                        .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ContentAnalysisController(
                        service, currentUserProvider))
                .build();

        mockMvc.perform(get("/api/content-analysis/tasks/91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status")
                        .value("FAILED"))
                .andExpect(jsonPath("$.data.analysisTypes[0]")
                        .value("SUMMARY"));
    }
}
