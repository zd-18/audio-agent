package com.audioagent.agent.service;

import com.audioagent.agent.dto.CreateAgentConversationRequest;
import com.audioagent.agent.dto.SendAgentMessageRequest;
import com.audioagent.agent.mapper.AgentConversationMapper;
import com.audioagent.agent.mapper.AgentMessageCitationMapper;
import com.audioagent.agent.vo.AgentConversationVO;
import com.audioagent.agent.mapper.AgentMessageMapper;
import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.AiChatRequest;
import com.audioagent.ai.AiChatResponse;
import com.audioagent.ai.exception.AiTimeoutException;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agentchat;"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:agent-test-schema.sql",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "audio-agent.ai.deepseek.enabled=false",
        "audio.analysis.dispatch-mode=local",
        "audio.processing.enabled=false",
        "minio.secret-key=test-secret-key",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentChatPersistenceIntegrationTest {

    @Autowired
    private AgentConversationService conversationService;

    @Autowired
    private AgentChatService chatService;

    @Autowired
    private AgentConversationMapper conversationMapper;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AgentMessageCitationMapper citationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AiChatClient aiChatClient;

    @MockitoBean
    private MinioClient minioClient;

    @BeforeEach
    void resetDatabase() {
        reset(aiChatClient);
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM audio_processing_plan");
        jdbcTemplate.update("DELETE FROM audio_processing_confirmation");
        jdbcTemplate.update("DELETE FROM audio_processing_step");
        jdbcTemplate.update("DELETE FROM audio_processing_step_confirmation");
        jdbcTemplate.update("DELETE FROM agent_processing_workflow");
        jdbcTemplate.update("DELETE FROM agent_message_citation");
        jdbcTemplate.update("DELETE FROM agent_message");
        jdbcTemplate.update("DELETE FROM agent_conversation");
        jdbcTemplate.update("DELETE FROM audio_transcript_segment");
        jdbcTemplate.update("DELETE FROM audio_transcript");
        jdbcTemplate.update("DELETE FROM audio_file");
        seedTranscript(7L, 11L, 81L, 301L,
                "meeting.mp3", "这段音频强调真实引用和可追溯性。");
        seedTranscript(8L, 12L, 82L, 302L,
                "private.mp3", "其他用户的文字稿。");
    }

    @Test
    void processingConversationCreatedForAudioFileWithoutTranscript()
            throws Exception {
        // 未转写 / 转写失败：该 audio_file 不存在任何 audio_transcript 记录，
        // 仅凭 audioFileId 仍能进入音频处理 Agent。
        seedAudioFileWithoutTranscript(7L, 83L, "cleanup.mp3", 5_000);
        seedSuccessfulAnalysisTask(41L, 83L);

        AgentConversationVO conversation = conversationService.create(7L,
                audioFileRequest("83", null));
        assertEquals("cleanup.mp3", conversation.getTitle());
        assertEquals("83", conversation.getAudioFileId());
        assertNull(conversation.getTranscriptId());

        when(aiChatClient.chat(any())).thenReturn(processingResponse());
        var result = chatService.send(7L, conversation.getConversationId(),
                processingRequest("压缩过长的停顿", "processing-1"));

        assertNotNull(result.getProcessingWorkflow());
        assertEquals("WAITING_CONFIRMATION",
                result.getProcessingWorkflow().getStatus());
        assertEquals("83", result.getProcessingWorkflow()
                .getAudioFileId().toString());
        verify(aiChatClient).chat(any());
    }

    @Test
    void processingConversationWorksWhenTranscriptHasNoSpeech()
            throws Exception {
        // 无人声：即使转写结果为空，也不影响进入音频处理 Agent。
        seedTranscript(7L, 84L, 91L, 401L, "silent.mp3", "");
        seedSuccessfulAnalysisTask(42L, 84L);

        AgentConversationVO conversation = conversationService.create(7L,
                audioFileRequest("84", null));

        when(aiChatClient.chat(any())).thenReturn(processingResponse());
        var result = chatService.send(7L, conversation.getConversationId(),
                processingRequest("删除所有静音", "processing-silent"));

        assertNotNull(result.getProcessingWorkflow());
        assertEquals("84", result.getProcessingWorkflow()
                .getAudioFileId().toString());
    }

    @Test
    void contentChatStillRequiresTranscriptOnProcessingConversation() {
        seedAudioFileWithoutTranscript(7L, 85L, "chatless.mp3", 3_000);
        seedSuccessfulAnalysisTask(43L, 85L);
        AgentConversationVO conversation = conversationService.create(7L,
                audioFileRequest("85", null));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> chatService.send(7L, conversation.getConversationId(),
                        messageRequest("这段音频讲了什么？", "chat-1")));
        assertEquals(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND.getCode(),
                failure.getCode());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> conversationService.create(7L,
                        createRequest("999", null)));
        assertEquals(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND.getCode(),
                missing.getCode());
    }

    @Test
    void createConversationRequiresEitherTranscriptOrAudioFile() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> conversationService.create(7L,
                        new CreateAgentConversationRequest()));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(),
                failure.getCode());
    }

    @Test
    void processingConversationRejectsForeignAudioFile() {
        // 81/82 属于用户 7，文件 83 只属于用户 8。
        seedAudioFileWithoutTranscript(8L, 83L, "foreign.mp3", 5_000);
        BusinessException failure = assertThrows(BusinessException.class,
                () -> conversationService.create(7L,
                        audioFileRequest("83", null)));
        assertEquals(ErrorCode.AUDIO_FILE_NOT_FOUND.getCode(),
                failure.getCode());
    }

    @Test
    void createsMultipleOwnedConversationsAndRejectsForeignTranscript() {
        var first = conversationService.create(7L,
                createRequest("81", null));
        var second = conversationService.create(7L,
                createRequest("81", "第二个会话"));

        assertEquals("meeting.mp3", first.getTitle());
        assertEquals("ACTIVE", first.getStatus());
        assertEquals("transcript-chat-v1", first.getPromptVersion());
        assertTrue(!first.getConversationId()
                .equals(second.getConversationId()));
        assertEquals(2, conversationService.list(
                7L, 1, 20, "81", null, "ACTIVE").getTotal());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> conversationService.create(7L,
                        createRequest("999", null)));
        assertEquals(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND.getCode(),
                missing.getCode());
        BusinessException foreign = assertThrows(BusinessException.class,
                () -> conversationService.create(7L,
                        createRequest("82", null)));
        assertEquals(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND.getCode(),
                foreign.getCode());

        String foreignConversation = conversationService.create(8L,
                createRequest("82", null)).getConversationId();
        BusinessException accessDenied = assertThrows(
                BusinessException.class,
                () -> conversationService.get(7L, foreignConversation));
        assertEquals(ErrorCode.AGENT_CONVERSATION_NOT_FOUND.getCode(),
                accessDenied.getCode());
    }

    @Test
    void foreignConversationCannotListOrSendMessagesAndCreatesNothing() {
        String foreignConversation = conversationService.create(8L,
                createRequest("82", null)).getConversationId();
        int before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message", Integer.class);

        BusinessException listError = assertThrows(BusinessException.class,
                () -> chatService.listMessages(7L, foreignConversation,
                        1, 20));
        BusinessException sendError = assertThrows(BusinessException.class,
                () -> chatService.send(7L, foreignConversation,
                        messageRequest("不能访问", "foreign-1")));

        assertEquals(ErrorCode.AGENT_CONVERSATION_NOT_FOUND.getCode(),
                listError.getCode());
        assertEquals(listError.getCode(), sendError.getCode());
        assertEquals(listError.getMessage(), sendError.getMessage());
        assertEquals(before, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message", Integer.class));
    }

    @Test
    void conversationListOrdersMostRecentlyMessagedConversationFirst() {
        String first = conversationService.create(7L,
                createRequest("81", "first")).getConversationId();
        String second = conversationService.create(7L,
                createRequest("81", "second")).getConversationId();
        when(aiChatClient.chat(any())).thenReturn(response("最近回答"));

        chatService.send(7L, first,
                messageRequest("刷新第一个会话", "sort-1"));

        var page = conversationService.list(
                7L, 1, 1, "81", null, "ACTIVE");
        assertEquals(2, page.getTotal());
        assertEquals(first, page.getRecords().getFirst()
                .getConversationId());
        assertTrue(!first.equals(second));
    }

    @Test
    void successfulSendPersistsOrderedMessagesTokensCitationAndIdempotency() {
        String conversationId = conversationService.create(7L,
                createRequest("81", null)).getConversationId();
        when(aiChatClient.chat(any())).thenReturn(response("第一轮回答"));
        SendAgentMessageRequest request = messageRequest(
                "这段音频表达了什么观点？", "request-1");

        var first = chatService.send(7L, conversationId, request);
        var duplicate = chatService.send(7L, conversationId, request);

        assertEquals(1, first.getUserMessage().getSequenceNo());
        assertEquals(2, first.getAssistantMessage().getSequenceNo());
        assertEquals("SUCCESS", first.getAssistantMessage().getStatus());
        assertEquals(11, first.getAssistantMessage().getPromptTokens());
        assertEquals(12,
                first.getAssistantMessage().getCompletionTokens());
        assertEquals(23, first.getAssistantMessage().getTotalTokens());
        assertEquals("301", first.getAssistantMessage()
                .getCitations().getFirst().getSegmentId());
        assertEquals(0L, first.getAssistantMessage()
                .getCitations().getFirst().getStartMs());
        assertEquals(6000L, first.getAssistantMessage()
                .getCitations().getFirst().getEndMs());
        assertEquals(first.getAssistantMessage().getMessageId(),
                duplicate.getAssistantMessage().getMessageId());
        verify(aiChatClient, times(1)).chat(any());

        var storedConversation = conversationMapper.selectById(
                Long.valueOf(conversationId));
        assertEquals(first.getAssistantMessage().getMessageId(),
                storedConversation.getLastMessageId().toString());
        assertNotNull(storedConversation.getLastMessageAt());
        assertEquals(1, citationMapper.selectByMessageId(7L,
                Long.valueOf(first.getAssistantMessage().getMessageId()))
                .size());
        assertEquals(2, chatService.listMessages(
                7L, conversationId, 1, 50).getTotal());
    }

    @Test
    void secondTurnReceivesOnlyPriorSuccessfulHistoryInSequenceOrder() {
        String conversationId = conversationService.create(7L,
                createRequest("81", null)).getConversationId();
        when(aiChatClient.chat(any()))
                .thenReturn(response("第一轮回答"), response("第二轮回答"));

        chatService.send(7L, conversationId,
                messageRequest("第一轮问题", "history-1"));
        chatService.send(7L, conversationId,
                messageRequest("第二轮追问", "history-2"));

        ArgumentCaptor<AiChatRequest> captor =
                ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient, times(2)).chat(captor.capture());
        AiChatRequest second = captor.getAllValues().get(1);
        assertEquals("system", second.messages().get(0).role());
        assertEquals("第一轮问题", second.messages().get(1).content());
        assertEquals("第一轮回答", second.messages().get(2).content());
        assertTrue(second.messages().get(3).content()
                .contains("第二轮追问"));

        var messages = chatService.listMessages(
                7L, conversationId, 1, 50).getRecords();
        assertEquals(List.of(1, 2, 3, 4), messages.stream()
                .map(item -> item.getSequenceNo()).toList());
    }

    @Test
    void aiFailureKeepsUserAndMarksAssistantFailed() {
        String conversationId = conversationService.create(7L,
                createRequest("81", null)).getConversationId();
        when(aiChatClient.chat(any())).thenThrow(
                new AiTimeoutException("timeout", null));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> chatService.send(7L, conversationId,
                        messageRequest("会超时的问题", "timeout-1")));

        assertEquals(ErrorCode.AGENT_AI_TIMEOUT.getCode(),
                failure.getCode());
        var messages = chatService.listMessages(
                7L, conversationId, 1, 50).getRecords();
        assertEquals(2, messages.size());
        assertEquals("SUCCESS", messages.get(0).getStatus());
        assertEquals("FAILED", messages.get(1).getStatus());
        assertEquals("AGENT_AI_TIMEOUT",
                messages.get(1).getFailureCode());

        jdbcTemplate.update("""
                UPDATE agent_message
                SET content = 'FAILED_SHOULD_NOT_APPEAR'
                WHERE conversation_id = ? AND status = 'FAILED'
                """, Long.valueOf(conversationId));
        reset(aiChatClient);
        when(aiChatClient.chat(any())).thenReturn(response("恢复后的回答"));
        chatService.send(7L, conversationId,
                messageRequest("下一轮问题", "after-failure"));
        ArgumentCaptor<AiChatRequest> requestCaptor =
                ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient).chat(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().messages().stream()
                .noneMatch(item -> item.content()
                        .contains("FAILED_SHOULD_NOT_APPEAR")));
    }

    @Test
    void concurrentRequestsAllocateUniqueStrictlyIncreasingSequences()
            throws Exception {
        String conversationId = conversationService.create(7L,
                createRequest("81", null)).getConversationId();
        when(aiChatClient.chat(any())).thenReturn(response("并发回答"));

        CompletableFuture<Void> first = CompletableFuture.runAsync(() ->
                chatService.send(7L, conversationId,
                        messageRequest("并发问题一", "parallel-1")));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() ->
                chatService.send(7L, conversationId,
                        messageRequest("并发问题二", "parallel-2")));
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

        List<Integer> sequences = new ArrayList<>(chatService.listMessages(
                7L, conversationId, 1, 50).getRecords().stream()
                .map(item -> item.getSequenceNo()).toList());
        assertEquals(List.of(1, 2, 3, 4), sequences);
        assertEquals(4, sequences.stream().distinct().count());
        var records = chatService.listMessages(
                7L, conversationId, 1, 50).getRecords();
        assertEquals(records.get(3).getMessageId(),
                conversationMapper.selectById(Long.valueOf(conversationId))
                        .getLastMessageId().toString());
    }

    private CreateAgentConversationRequest createRequest(
            String transcriptId, String title) {
        CreateAgentConversationRequest request =
                new CreateAgentConversationRequest();
        request.setTranscriptId(transcriptId);
        request.setTitle(title);
        return request;
    }

    private CreateAgentConversationRequest audioFileRequest(
            String audioFileId, String title) {
        CreateAgentConversationRequest request =
                new CreateAgentConversationRequest();
        request.setAudioFileId(audioFileId);
        request.setTitle(title);
        return request;
    }

    private SendAgentMessageRequest messageRequest(
            String content, String clientRequestId) {
        SendAgentMessageRequest request = new SendAgentMessageRequest();
        request.setContent(content);
        request.setClientRequestId(clientRequestId);
        return request;
    }

    private SendAgentMessageRequest processingRequest(
            String content, String clientRequestId) {
        SendAgentMessageRequest request = messageRequest(
                content, clientRequestId);
        request.setMode("PROCESSING");
        return request;
    }

    private AiChatResponse response(String answer) {
        return new AiChatResponse("""
                {"answer":"%s","insufficientContext":false,
                 "citations":[{"segmentId":"301",
                 "quote":"这段音频强调真实引用"}]}
                """.formatted(answer), 11, 12, 23, "deepseek-test");
    }

    private AiChatResponse processingResponse() {
        return new AiChatResponse("""
                {"summary":"压缩长静音","steps":[
                  {"order":1,"operationType":"SILENCE_CLEANUP",
                   "parameters":{"mode":"COMPRESS","minSilenceMs":3000,
                                 "keepSilenceMs":800},
                   "startMs":null,"endMs":null,"reason":"检测到长停顿"}]}
                """, 21, 22, 43, "deepseek-test");
    }

    /** 只有音频文件、没有任何转写记录的音频（未转写或转写失败场景）。 */
    private void seedAudioFileWithoutTranscript(long userId, long fileId,
                                                String fileName,
                                                long durationMs) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO audio_file (
                    id, user_id, file_role, original_name, extension,
                    mime_type, bucket_name, object_key, size_bytes,
                    duration_ms, file_status, created_at, updated_at, deleted
                ) VALUES (?, ?, 1, ?, 'mp3', 'audio/mpeg', 'bucket',
                          ?, 100, ?, 2, ?, ?, 0)
                """, fileId, userId, fileName,
                "audio/" + fileName, durationMs, now, now);
    }

    private void seedSuccessfulAnalysisTask(long taskId, long fileId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO audio_analysis_task (
                    id, audio_file_id, status, progress,
                    created_at, updated_at, finished_at
                ) VALUES (?, ?, 'SUCCESS', 100, ?, ?, ?)
                """, taskId, fileId, now, now, now);
    }

    private void seedTranscript(long userId, long fileId,
                                long transcriptId, long segmentId,
                                String fileName, String text) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO audio_file (
                    id, user_id, file_role, original_name, extension,
                    mime_type, bucket_name, object_key, size_bytes,
                    duration_ms, file_status, created_at, updated_at, deleted
                ) VALUES (?, ?, 1, ?, 'mp3', 'audio/mpeg', 'bucket',
                          ?, 100, 6000, 2, ?, ?, 0)
                """, fileId, userId, fileName,
                "audio/" + fileName, now, now);
        jdbcTemplate.update("""
                INSERT INTO audio_transcript (
                    id, user_id, audio_file_id, transcription_task_id,
                    language, full_text, duration_ms, speaker_count,
                    segment_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'zh', ?, 6000, 1, 1, ?, ?)
                """, transcriptId, userId, fileId,
                transcriptId + 1000, text, now, now);
        jdbcTemplate.update("""
                INSERT INTO audio_transcript_segment (
                    id, user_id, transcript_id, segment_order,
                    start_ms, end_ms, speaker_label, text,
                    confidence, created_at
                ) VALUES (?, ?, ?, 1, 0, 6000, 'speaker-1',
                          ?, 0.99, ?)
                """, segmentId, userId, transcriptId, text, now);
    }
}
