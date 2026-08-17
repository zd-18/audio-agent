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
        // 音频处理会话直接基于 AudioFile；转写只是可选的补充上下文，
        // 未转写、转写失败或无人声的音频仍可进入处理工作流。
        Long audioFileId = conversation.getAudioFileId();
        String transcriptContent = "";
        Long transcriptId = conversation.getTranscriptId();
        if (transcriptId != null) {
            AudioTranscript transcript = transcriptMapper.selectOwned(
                    userId, transcriptId);
            if (transcript != null) {
                if (audioFileId == null) {
                    audioFileId = transcript.getAudioFileId();
                }
                TranscriptChatContext transcriptContext =
                        transcriptContextService.build(userId,
                                transcriptId, requirement);
                transcriptContent = transcriptContext.content();
            }
        }
        if (audioFileId == null) {
            throw unavailable("The source audio file is unavailable");
        }
        AudioFile file = audioFileMapper.selectById(audioFileId);
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
        return new AgentProcessingContext(task.getId(), file.getId(),
                file.getOriginalName(), file.getDurationMs(),
                file.getSampleRate(), file.getChannels(),
                transcriptContent);
    }

    private AgentExecutionException unavailable(String message) {
        return new AgentExecutionException(
                ErrorCode.AGENT_PROCESSING_CONTEXT_UNAVAILABLE,
                false, message);
    }
}
