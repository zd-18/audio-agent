package com.audioagent.contentanalysis.client;

import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.AiChatRequest;
import com.audioagent.ai.AiChatResponse;
import com.audioagent.ai.exception.AiPaymentRequiredException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekClientAdapterTest {

    private final AiChatClient aiChatClient = mock(AiChatClient.class);
    private DeepSeekProperties properties;
    private DeepSeekClientAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setApiKey("test-only-key");
        adapter = new DeepSeekClientAdapter(aiChatClient, properties);
    }

    @Test
    void preservesContentAnalysisJsonRequestAndUsageMapping() {
        when(aiChatClient.chat(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiChatResponse(
                        "{\"summary\":{}}", 1, 2, 3, "deepseek-test"));

        DeepSeekResponse response = adapter.complete("system", "user");

        ArgumentCaptor<AiChatRequest> captor =
                ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient).chat(captor.capture());
        assertEquals("json_object",
                captor.getValue().responseFormat().get("type"));
        assertEquals(properties.getMaxTokens(),
                captor.getValue().maxTokens());
        assertEquals(3, response.totalTokens());
    }

    @Test
    void preservesNotConfiguredAndBalanceErrorCategories() {
        properties.setApiKey("");
        ContentAnalysisException unconfigured = assertThrows(
                ContentAnalysisException.class,
                () -> adapter.complete("system", "user"));
        assertEquals(ErrorCode.AI_SERVICE_NOT_CONFIGURED,
                unconfigured.getErrorCode());

        properties.setApiKey("test-only-key");
        when(aiChatClient.chat(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AiPaymentRequiredException("balance"));
        ContentAnalysisException balance = assertThrows(
                ContentAnalysisException.class,
                () -> adapter.complete("system", "user"));
        assertEquals(ErrorCode.AI_BALANCE_INSUFFICIENT,
                balance.getErrorCode());
    }
}
