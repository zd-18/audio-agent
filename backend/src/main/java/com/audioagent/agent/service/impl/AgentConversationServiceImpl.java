package com.audioagent.agent.service.impl;

import com.audioagent.agent.config.AgentProperties;
import com.audioagent.agent.dto.CreateAgentConversationRequest;
import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.mapper.AgentConversationMapper;
import com.audioagent.agent.model.AgentConversationStatus;
import com.audioagent.agent.service.AgentConversationService;
import com.audioagent.agent.vo.AgentConversationVO;
import com.audioagent.common.api.PageResult;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.common.exception.BusinessException;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentConversationServiceImpl
        implements AgentConversationService {

    private static final String FALLBACK_TITLE = "关于音频的对话";

    private final AgentConversationMapper conversationMapper;
    private final AudioTranscriptMapper transcriptMapper;
    private final AudioFileMapper audioFileMapper;
    private final AgentProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentConversationVO create(
            Long userId, CreateAgentConversationRequest request) {
        requireUser(userId);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        Long transcriptId = parseId(request.getTranscriptId(),
                "transcriptId is invalid");
        AudioTranscript transcript = transcriptMapper.selectOwned(
                userId, transcriptId);
        if (transcript == null) {
            throw new BusinessException(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND);
        }
        AudioFile audioFile = ownedAudioFile(userId,
                transcript.getAudioFileId());
        String defaultTitle = audioFile != null
                && StringUtils.hasText(audioFile.getOriginalName())
                ? audioFile.getOriginalName().trim() : FALLBACK_TITLE;
        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle().trim() : defaultTitle;
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }

        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(userId);
        conversation.setTranscriptId(transcriptId);
        conversation.setTitle(title);
        conversation.setStatus(AgentConversationStatus.ACTIVE);
        conversation.setModelName(properties.getModelName());
        conversation.setPromptVersion(properties.getPromptVersion());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setDeleted(0);
        if (conversationMapper.insert(conversation) != 1
                || conversation.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Agent conversation could not be created");
        }
        log.info("Agent conversation created, userId={}, "
                        + "conversationId={}, transcriptId={}",
                userId, conversation.getId(), transcriptId);
        return AgentConversationVO.from(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AgentConversationVO> list(
            Long userId, int current, int size,
            String transcriptId, String status) {
        requireUser(userId);
        requirePage(current, size, 100);
        Long parsedTranscriptId = StringUtils.hasText(transcriptId)
                ? parseId(transcriptId, "transcriptId is invalid") : null;
        String normalizedStatus = null;
        if (StringUtils.hasText(status)) {
            try {
                normalizedStatus = AgentConversationStatus.valueOf(
                        status.trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "status is invalid");
            }
        }
        IPage<AgentConversation> page = conversationMapper.selectOwnedPage(
                new Page<>(current, size), userId,
                parsedTranscriptId, normalizedStatus);
        return PageResult.of(page.getRecords().stream()
                        .map(AgentConversationVO::from).toList(),
                page.getCurrent(), page.getSize(), page.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentConversationVO get(Long userId, String conversationId) {
        requireUser(userId);
        AgentConversation conversation = requireOwned(
                userId, parseId(conversationId,
                        "conversationId is invalid"));
        AudioTranscript transcript = transcriptMapper.selectOwned(
                userId, conversation.getTranscriptId());
        if (transcript == null) {
            throw new BusinessException(ErrorCode.AGENT_TRANSCRIPT_NOT_FOUND);
        }
        AudioFile audioFile = ownedAudioFile(userId,
                transcript.getAudioFileId());
        return AgentConversationVO.detailed(conversation,
                audioFile == null ? null : audioFile.getOriginalName(),
                transcript.getDurationMs() != null
                        ? transcript.getDurationMs()
                        : audioFile == null ? null : audioFile.getDurationMs());
    }

    private AgentConversation requireOwned(Long userId,
                                           Long conversationId) {
        AgentConversation any = conversationMapper.selectById(conversationId);
        if (any == null) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_NOT_FOUND);
        }
        if (!userId.equals(any.getUserId())) {
            throw new BusinessException(
                    ErrorCode.AGENT_CONVERSATION_ACCESS_DENIED);
        }
        return any;
    }

    private AudioFile ownedAudioFile(Long userId, Long audioFileId) {
        if (audioFileId == null) {
            return null;
        }
        AudioFile audioFile = audioFileMapper.selectById(audioFileId);
        return audioFile != null && userId.equals(audioFile.getUserId())
                ? audioFile : null;
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

    private void requirePage(int current, int size, int maxSize) {
        if (current < 1 || size < 1 || size > maxSize) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "pagination parameters are invalid");
        }
    }
}
