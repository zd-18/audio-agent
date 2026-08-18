package com.audioagent.agent.service.impl;

import com.audioagent.agent.config.AgentProperties;
import com.audioagent.agent.context.TranscriptChatContext;
import com.audioagent.agent.context.TranscriptChatContextService;
import com.audioagent.agent.dto.SendAgentMessageRequest;
import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.entity.AgentMessage;
import com.audioagent.agent.entity.AgentMessageCitation;
import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.agent.mapper.AgentConversationMapper;
import com.audioagent.agent.mapper.AgentMessageCitationMapper;
import com.audioagent.agent.mapper.AgentMessageMapper;
import com.audioagent.agent.model.AgentConversationStatus;
import com.audioagent.agent.model.AgentMessageRole;
import com.audioagent.agent.model.AgentMessageStatus;
import com.audioagent.agent.model.AgentModelResponse;
import com.audioagent.agent.model.AgentRequestMode;
import com.audioagent.agent.prompt.TranscriptChatPromptV1;
import com.audioagent.agent.service.AgentChatService;
import com.audioagent.agent.validation.AgentCitationValidator;
import com.audioagent.agent.validation.AgentCitationValidator.ValidatedCitation;
import com.audioagent.agent.validation.AgentResponseParser;
import com.audioagent.agent.vo.AgentCitationVO;
import com.audioagent.agent.vo.AgentMessageVO;
import com.audioagent.agent.vo.SendAgentMessageVO;
import com.audioagent.agent.workflow.model.AgentWorkflowPlanningResult;
import com.audioagent.agent.workflow.service.AgentProcessingWorkflowService;
import com.audioagent.agent.workflow.vo.AgentProcessingWorkflowVO;
import com.audioagent.ai.AiChatClient;
import com.audioagent.ai.AiChatMessage;
import com.audioagent.ai.AiChatRequest;
import com.audioagent.ai.AiChatResponse;
import com.audioagent.ai.exception.AiClientException;
import com.audioagent.ai.exception.AiTimeoutException;
import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentChatServiceImpl implements AgentChatService {

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentMessageCitationMapper citationMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final TranscriptChatContextService contextService;
    private final TranscriptChatPromptV1 prompt;
    private final AgentResponseParser responseParser;
    private final AgentCitationValidator citationValidator;
    private final AiChatClient aiChatClient;
    private final AgentProperties properties;
    private final AgentProcessingWorkflowService workflowService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageResult<AgentMessageVO> listMessages(
            Long userId, String conversationId, int current, int size) {
        requireUser(userId);
        requirePage(current, size);
        Long id = parseId(conversationId, "conversationId is invalid");
        requireOwnedConversation(userId, id);
        IPage<AgentMessage> page = messageMapper
                .selectOwnedPageByConversationId(
                        new Page<>(current, size), userId, id);
        List<Long> assistantIds = page.getRecords().stream()
                .filter(message -> message.getRole()
                        == AgentMessageRole.ASSISTANT)
                .map(AgentMessage::getId)
                .toList();
        Map<Long, List<AgentCitationVO>> citationsByMessage =
                loadCitations(userId, assistantIds);
        List<AgentMessageVO> records = page.getRecords().stream()
                .map(message -> AgentMessageVO.from(message,
                        citationsByMessage.getOrDefault(
                                message.getId(), List.of())))
                .toList();
        return PageResult.of(records, page.getCurrent(), page.getSize(),
                page.getTotal());
    }

    @Override
    public SendAgentMessageVO send(
            Long userId, String conversationId,
            SendAgentMessageRequest request) {
        requireUser(userId);
        Long id = parseId(conversationId, "conversationId is invalid");
        ValidatedRequest validated = validateRequest(request);

        Preparation preparation = transactionTemplate.execute(status ->
                prepare(userId, id, validated));
        if (preparation == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Agent message could not be prepared");
        }
        if (preparation.duplicate()) {
            return toSendVO(userId, preparation.userMessage(),
                    preparation.assistantMessage());
        }

        try {
            return validated.mode() == AgentRequestMode.PROCESSING
                    ? executeProcessing(userId, preparation,
                    validated.content())
                    : execute(userId, preparation, validated.content());
        } catch (RuntimeException error) {
            AgentFailure failure = classify(error);
            Throwable rootCause = NestedExceptionUtils
                    .getMostSpecificCause(error);
            markFailed(userId, preparation.assistantMessage().getId(),
                    failure);
            log.warn("Agent request failed, conversationId={}, "
                            + "messageId={}, transcriptId={}, failureCode={}, "
                            + "exceptionType={}, exceptionMessage={}, "
                            + "rootCauseType={}, rootCauseMessage={}, "
                            + "retryable={}",
                    id, preparation.assistantMessage().getId(),
                    preparation.conversation().getTranscriptId(),
                    failure.code().name(),
                    error.getClass().getSimpleName(), error.getMessage(),
                    rootCause.getClass().getSimpleName(),
                    rootCause.getMessage(), failure.retryable());
            throw new BusinessException(failure.code(),
                    failure.code().getMessage());
        }
    }

    private Preparation prepare(Long userId, Long conversationId,
                                ValidatedRequest request) {
        AgentConversation conversation = conversationMapper.lockById(
                conversationId);
        if (conversation == null) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_NOT_FOUND,
                    "资源不存在或不可访问");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_NOT_FOUND,
                    "资源不存在或不可访问");
        }

        AgentMessage existing = messageMapper
                .selectByConversationAndClientRequestId(userId,
                        conversationId, request.clientRequestId());
        if (existing != null) {
            AgentMessage reply = messageMapper.selectReplyByUserMessageId(
                    userId, conversationId, existing.getId());
            if (reply == null) {
                throw new BusinessException(
                        ErrorCode.AGENT_MESSAGE_DUPLICATE,
                        "The original request is incomplete");
            }
            return new Preparation(conversation, existing, reply, true);
        }

        if (conversation.getStatus() != AgentConversationStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_STATUS_INVALID);
        }
        Long transcriptId = conversation.getTranscriptId();
        if (request.mode() == AgentRequestMode.CHAT
                && transcriptId == null) {
            // 内容问答依赖文字稿；没有文字稿的会话只允许音频处理。
            throw new BusinessException(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND,
                    "Content Q&A requires a transcript");
        }
        if (transcriptId != null) {
            AudioTranscript transcript = transcriptMapper.selectOwned(
                    userId, transcriptId);
            if (transcript == null) {
                throw new BusinessException(
                        ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND);
            }
        }

        int sequence = messageMapper.selectMaxSequenceNo(conversationId);
        LocalDateTime now = LocalDateTime.now();
        AgentMessage userMessage = new AgentMessage();
        userMessage.setUserId(userId);
        userMessage.setConversationId(conversationId);
        userMessage.setSequenceNo(sequence + 1);
        userMessage.setRole(AgentMessageRole.USER);
        userMessage.setContent(request.content());
        userMessage.setStatus(AgentMessageStatus.SUCCESS);
        userMessage.setClientRequestId(request.clientRequestId());
        userMessage.setCreatedAt(now);
        userMessage.setUpdatedAt(now);
        if (messageMapper.insert(userMessage) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "User message could not be stored");
        }

        AgentMessage assistant = new AgentMessage();
        assistant.setUserId(userId);
        assistant.setConversationId(conversationId);
        assistant.setSequenceNo(sequence + 2);
        assistant.setRole(AgentMessageRole.ASSISTANT);
        assistant.setStatus(AgentMessageStatus.PROCESSING);
        assistant.setReplyToMessageId(userMessage.getId());
        assistant.setModelName(conversation.getModelName());
        assistant.setPromptVersion(conversation.getPromptVersion());
        assistant.setStartedAt(now);
        assistant.setCreatedAt(now);
        assistant.setUpdatedAt(now);
        if (messageMapper.insert(assistant) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Assistant message could not be stored");
        }
        log.info("Agent question accepted, conversationId={}, "
                        + "userMessageId={}, assistantMessageId={}, "
                        + "questionLength={}, clientRequestId={}",
                conversationId, userMessage.getId(), assistant.getId(),
                request.content().length(), request.clientRequestId());
        return new Preparation(conversation, userMessage, assistant, false);
    }

    private SendAgentMessageVO execute(Long userId, Preparation preparation,
                                       String question) {
        AgentConversation conversation = preparation.conversation();
        TranscriptChatContext context = contextService.build(userId,
                conversation.getTranscriptId(), question);
        List<AgentMessage> history = messageMapper
                .selectRecentSuccessMessages(userId, conversation.getId(),
                        preparation.userMessage().getSequenceNo(),
                        properties.getHistoryMessageLimit());
        history = new ArrayList<>(history);
        Collections.reverse(history);

        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(prompt.systemPrompt()));
        for (AgentMessage item : history) {
            if (!StringUtils.hasText(item.getContent())) {
                continue;
            }
            messages.add(item.getRole() == AgentMessageRole.USER
                    ? AiChatMessage.user(item.getContent())
                    : AiChatMessage.assistant(item.getContent()));
        }
        messages.add(AiChatMessage.user(prompt.userPrompt(
                context, question)));
        log.info("Agent prompt ready, conversationId={}, transcriptId={}, "
                        + "historyMessageCount={}, contextChars={}",
                conversation.getId(), conversation.getTranscriptId(),
                history.size(), context.contextChars());

        long started = System.nanoTime();
        AiChatResponse aiResponse = aiChatClient.chat(new AiChatRequest(
                conversation.getModelName(), messages,
                properties.getTemperature(), properties.getAnswerMaxTokens(),
                Map.of("type", "json_object"), Duration.ofSeconds(
                properties.getRequestTimeoutSeconds())));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        log.info("Agent AI call completed, modelName={}, elapsedMs={}, "
                        + "promptTokens={}, completionTokens={}, totalTokens={}",
                aiResponse.model(), elapsedMs, aiResponse.promptTokens(),
                aiResponse.completionTokens(), aiResponse.totalTokens());

        AgentModelResponse parsed = responseParser.parse(
                aiResponse.content());
        List<ValidatedCitation> validatedCitations =
                citationValidator.validate(userId,
                        conversation.getTranscriptId(), parsed,
                        context.segments());
        Completion completion = transactionTemplate.execute(status ->
                complete(userId, preparation, parsed,
                        validatedCitations, aiResponse));
        if (completion == null) {
            throw new AgentExecutionException(
                    ErrorCode.AGENT_AI_UNAVAILABLE, false,
                    "Agent response could not be stored");
        }
        return SendAgentMessageVO.builder()
                .userMessage(AgentMessageVO.from(
                        preparation.userMessage(), List.of()))
                .assistantMessage(AgentMessageVO.from(
                        completion.message(), completion.citations().stream()
                                .map(AgentCitationVO::from).toList()))
                .build();
    }

    private SendAgentMessageVO executeProcessing(
            Long userId, Preparation preparation, String requirement) {
        AgentWorkflowPlanningResult planned = workflowService.plan(userId,
                preparation.conversation(), preparation.userMessage(),
                preparation.assistantMessage(), requirement);
        AgentModelResponse response = new AgentModelResponse();
        response.setAnswer(planned.assistantMessage());
        response.setInsufficientContext(true);
        response.setCitations(List.of());
        AiChatResponse aiResponse = new AiChatResponse("",
                planned.promptTokens(), planned.completionTokens(),
                planned.totalTokens(), planned.modelName());
        Completion completion = transactionTemplate.execute(status ->
                complete(userId, preparation, response, List.of(),
                        aiResponse));
        if (completion == null) {
            throw new AgentExecutionException(ErrorCode.AGENT_PLAN_FAILED,
                    false, "Agent processing plan could not be stored");
        }
        return SendAgentMessageVO.builder()
                .userMessage(AgentMessageVO.from(
                        preparation.userMessage(), List.of()))
                .assistantMessage(AgentMessageVO.from(
                        completion.message(), List.of()))
                .processingWorkflow(planned.workflow())
                .build();
    }

    private Completion complete(Long userId, Preparation preparation,
                                AgentModelResponse parsed,
                                List<ValidatedCitation> citations,
                                AiChatResponse aiResponse) {
        LocalDateTime finishedAt = LocalDateTime.now();
        AgentMessage assistant = preparation.assistantMessage();
        String modelName = StringUtils.hasText(aiResponse.model())
                ? aiResponse.model()
                : preparation.conversation().getModelName();
        int updated = messageMapper.updateSuccess(userId,
                assistant.getId(), parsed.getAnswer(), modelName,
                preparation.conversation().getPromptVersion(),
                aiResponse.promptTokens(), aiResponse.completionTokens(),
                aiResponse.totalTokens(), finishedAt);
        if (updated != 1) {
            throw new AgentExecutionException(
                    ErrorCode.AGENT_RESPONSE_INVALID, false,
                    "Assistant message state changed unexpectedly");
        }

        List<AgentMessageCitation> entities = new ArrayList<>();
        for (int i = 0; i < citations.size(); i++) {
            ValidatedCitation source = citations.get(i);
            AgentMessageCitation citation = new AgentMessageCitation();
            citation.setId(IdWorker.getId());
            citation.setUserId(userId);
            citation.setConversationId(
                    preparation.conversation().getId());
            citation.setMessageId(assistant.getId());
            citation.setTranscriptId(
                    preparation.conversation().getTranscriptId());
            citation.setSegmentId(source.segmentId());
            citation.setSegmentOrder(source.segmentOrder());
            citation.setStartMs(source.startMs());
            citation.setEndMs(source.endMs());
            citation.setQuote(source.quote());
            citation.setCitationOrder(i + 1);
            citation.setCreatedAt(finishedAt);
            entities.add(citation);
        }
        if (!entities.isEmpty()
                && citationMapper.batchInsert(entities) != entities.size()) {
            throw new AgentExecutionException(
                    ErrorCode.AGENT_CITATION_INVALID, false,
                    "Citations could not be stored");
        }
        conversationMapper.updateLastMessage(userId,
                preparation.conversation().getId(), assistant.getId(),
                assistant.getSequenceNo(), finishedAt);
        assistant.setContent(parsed.getAnswer());
        assistant.setStatus(AgentMessageStatus.SUCCESS);
        assistant.setModelName(modelName);
        assistant.setPromptTokens(aiResponse.promptTokens());
        assistant.setCompletionTokens(aiResponse.completionTokens());
        assistant.setTotalTokens(aiResponse.totalTokens());
        assistant.setFinishedAt(finishedAt);
        assistant.setUpdatedAt(finishedAt);
        return new Completion(assistant, entities);
    }

    private void markFailed(Long userId, Long assistantMessageId,
                            AgentFailure failure) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    messageMapper.updateFailed(userId, assistantMessageId,
                            failure.code().name(),
                            failure.code().getMessage(),
                            LocalDateTime.now()));
        } catch (RuntimeException persistenceFailure) {
            log.error("Failed to persist Agent failure state, messageId={}, "
                            + "failureCode={}",
                    assistantMessageId, failure.code().name(),
                    persistenceFailure);
        }
    }

    private AgentFailure classify(RuntimeException error) {
        if (error instanceof AgentExecutionException known) {
            return new AgentFailure(known.getErrorCode(),
                    known.isRetryable());
        }
        if (error instanceof AiTimeoutException) {
            return new AgentFailure(ErrorCode.AGENT_AI_TIMEOUT, true);
        }
        if (error instanceof AiClientException known) {
            return new AgentFailure(ErrorCode.AGENT_AI_UNAVAILABLE,
                    known.isRetryable());
        }
        return new AgentFailure(ErrorCode.AGENT_AI_UNAVAILABLE, false);
    }

    private SendAgentMessageVO toSendVO(Long userId,
                                        AgentMessage userMessage,
                                        AgentMessage assistant) {
        List<AgentCitationVO> citations = citationMapper
                .selectByMessageId(userId, assistant.getId()).stream()
                .map(AgentCitationVO::from).toList();
        return SendAgentMessageVO.builder()
                .userMessage(AgentMessageVO.from(userMessage, List.of()))
                .assistantMessage(AgentMessageVO.from(assistant, citations))
                .processingWorkflow(workflowService.findByUserMessage(
                        userId, userMessage.getId()))
                .build();
    }

    private Map<Long, List<AgentCitationVO>> loadCitations(
            Long userId, List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AgentCitationVO>> result = new HashMap<>();
        for (AgentMessageCitation citation
                : citationMapper.selectByMessageIds(userId, messageIds)) {
            result.computeIfAbsent(citation.getMessageId(),
                            ignored -> new ArrayList<>())
                    .add(AgentCitationVO.from(citation));
        }
        return result;
    }

    private AgentConversation requireOwnedConversation(
            Long userId, Long conversationId) {
        AgentConversation any = conversationMapper.selectOwnedById(
                userId, conversationId);
        if (any == null) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_NOT_FOUND,
                    "资源不存在或不可访问");
        }
        return any;
    }

    private ValidatedRequest validateRequest(
            SendAgentMessageRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ErrorCode.AGENT_MESSAGE_EMPTY);
        }
        String content = request.getContent().trim();
        if (content.length() > properties.getMaxQuestionChars()) {
            throw new BusinessException(ErrorCode.AGENT_MESSAGE_TOO_LONG);
        }
        if (!StringUtils.hasText(request.getClientRequestId())
                || request.getClientRequestId().trim().length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "clientRequestId is invalid");
        }
        AgentRequestMode mode;
        try {
            mode = AgentRequestMode.valueOf(
                    StringUtils.hasText(request.getMode())
                            ? request.getMode().trim().toUpperCase()
                            : AgentRequestMode.CHAT.name());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "Agent request mode is invalid");
        }
        return new ValidatedRequest(content,
                request.getClientRequestId().trim(), mode);
    }

    private Long parseId(String value, String message) {
        if (!StringUtils.hasText(value)
                || !value.matches("[1-9]\\d{0,18}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "userId is invalid");
        }
    }

    private void requirePage(int current, int size) {
        if (current < 1 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "pagination parameters are invalid");
        }
    }

    private record ValidatedRequest(String content,
                                    String clientRequestId,
                                    AgentRequestMode mode) {
    }

    private record Preparation(AgentConversation conversation,
                               AgentMessage userMessage,
                               AgentMessage assistantMessage,
                               boolean duplicate) {
    }

    private record Completion(AgentMessage message,
                              List<AgentMessageCitation> citations) {
    }

    private record AgentFailure(ErrorCode code, boolean retryable) {
    }
}
