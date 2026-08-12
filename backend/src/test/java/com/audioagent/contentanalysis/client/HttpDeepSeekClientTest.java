package com.audioagent.contentanalysis.client;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.contentanalysis.config.DeepSeekProperties;
import com.audioagent.contentanalysis.exception.ContentAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpDeepSeekClientTest {

    private MockRestServiceServer server;
    private HttpDeepSeekClient client;

    @BeforeEach
    void setUp() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-only-key");
        properties.setBaseUrl("https://deepseek.test");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpDeepSeekClient(builder.build(), properties);
    }

    @Test
    void parsesSuccessfulStructuredResponseAndUsage() {
        server.expect(once(),
                        requestTo("https://deepseek.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization",
                        "Bearer test-only-key"))
                .andExpect(jsonPath("$.model")
                        .value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.response_format.type")
                        .value("json_object"))
                .andExpect(jsonPath("$.max_tokens").value(8192))
                .andRespond(withSuccess("""
                        {
                          "model": "deepseek-test",
                          "choices": [{
                            "message": {"content": "{\\"summary\\":{}}"}
                          }],
                          "usage": {
                            "prompt_tokens": 11,
                            "completion_tokens": 22,
                            "total_tokens": 33
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        DeepSeekResponse response = client.complete(
                "必须返回 JSON", "JSON 示例 {}");

        assertEquals("{\"summary\":{}}", response.content());
        assertEquals("deepseek-test", response.model());
        assertEquals(33, response.totalTokens());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400,AI_REQUEST_INVALID,false",
            "401,AI_AUTH_FAILED,false",
            "402,AI_BALANCE_INSUFFICIENT,false",
            "422,AI_REQUEST_INVALID,false",
            "429,AI_RATE_LIMITED,true",
            "503,AI_SERVICE_UNAVAILABLE,true"
    })
    void classifiesHttpFailures(
            int status, ErrorCode expectedCode, boolean retryable) {
        server.expect(requestTo(
                        "https://deepseek.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> client.complete("JSON", "{}"));

        assertEquals(expectedCode, failure.getErrorCode());
        assertEquals(retryable, failure.isRetryable());
    }

    @Test
    void honorsNumericRetryAfterForRateLimit() {
        server.expect(requestTo(
                        "https://deepseek.test/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "7"));

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> client.complete("JSON", "{}"));

        assertTrue(failure.isRetryable());
        assertEquals(7_000L,
                failure.getRetryAfterMilliseconds());
    }

    @Test
    void emptyApiKeyFailsBeforeNetworkCall() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("");
        HttpDeepSeekClient unconfigured = new HttpDeepSeekClient(
                RestClient.create("https://deepseek.test"), properties);

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> unconfigured.complete("JSON", "{}"));

        assertEquals(ErrorCode.AI_SERVICE_NOT_CONFIGURED,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
    }

    @Test
    void socketTimeoutIsRetryable() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-only-key");
        RestClient throwingClient = mock(RestClient.class);
        when(throwingClient.post()).thenThrow(
                new ResourceAccessException("timed out",
                        new SocketTimeoutException("timed out")));
        HttpDeepSeekClient timeoutClient =
                new HttpDeepSeekClient(throwingClient, properties);

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> timeoutClient.complete("JSON", "{}"));

        assertEquals(ErrorCode.AI_TIMEOUT, failure.getErrorCode());
        assertTrue(failure.isRetryable());
    }

    @Test
    void nonTimeoutConnectionFailureIsNotRetried() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey("test-only-key");
        RestClient throwingClient = mock(RestClient.class);
        when(throwingClient.post()).thenThrow(
                new ResourceAccessException("connection refused",
                        new ConnectException("connection refused")));
        HttpDeepSeekClient unavailableClient =
                new HttpDeepSeekClient(throwingClient, properties);

        ContentAnalysisException failure = assertThrows(
                ContentAnalysisException.class,
                () -> unavailableClient.complete("JSON", "{}"));

        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE,
                failure.getErrorCode());
        assertFalse(failure.isRetryable());
    }
}
