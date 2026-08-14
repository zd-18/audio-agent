package com.audioagent.agent.workflow.service;

import com.audioagent.agent.context.TranscriptChatContext;
import com.audioagent.agent.context.TranscriptChatContextService;
import com.audioagent.agent.entity.AgentConversation;
import com.audioagent.agent.exception.AgentExecutionException;
import com.audioagent.agent.workflow.model.AgentProcessingContext;
import com.audioagent.analysis.entity.AudioAnalysisTask;
import com.audioagent.analysis.mapper.AudioAnalysisTaskMapper;
import com.audioagent.common.enums.ErrorCode;
import com.audioagent.file.entity.AudioFile;
import com.audioagent.file.mapper.AudioFileMapper;
import com.audioagent.transcription.entity.AudioTranscript;
import com.audioagent.transcription.mapper.AudioTranscriptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentProcessingContextService {

    private final AudioTranscriptMapper transcriptMapper;
    private final AudioFileMapper audioFileMapper;
    private final AudioAnalysisTaskMapper analysisTaskMapper;
    private final TranscriptChatContextService transcriptContextService;

    public AgentProcessingContext build(Long userId,
                                        AgentConversation conversation,
                                        String requirement) {
        AudioTranscript transcript = transcriptMapper.selectOwned(userId,
                conversation.getTranscriptId());
        if (transcript == null) {
            throw unavailable("The conversation transcript is unavailable");
        }
        AudioFile file = audioFileMapper.selectById(transcript.getAudioFileId());
        if (file == null || !userId.equals(file.getUserId())
                || Integer.valueOf(1).equals(file.getDeleted())
                || file.getDurationMs() == null || file.getDurationMs() <= 0) {
            throw unavailable("The source audio metadata is unavailable");
        }
        AudioAnalysisTask task = analysisTaskMapper
                .selectLatestSuccessfulByAudioFileId(file.getId());
        if (task == null) {
            throw unavailable("A completed audio analysis is required before planning");
        }
        TranscriptChatContext transcriptContext = transcriptContextService
                .build(userId, transcript.getId(), requirement);
        return new AgentProcessingContext(task.getId(), file.getId(),
                file.getOriginalName(), file.getDurationMs(),
                file.getSampleRate(), file.getChannels(),
                transcriptContext.content());
    }

    private AgentExecutionException unavailable(String message) {
        return new AgentExecutionException(
                ErrorCode.AGENT_PROCESSING_CONTEXT_UNAVAILABLE,
                false, message);
    }
}
