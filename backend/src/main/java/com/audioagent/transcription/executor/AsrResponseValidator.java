package com.audioagent.transcription.executor;

import com.audioagent.common.enums.ErrorCode;
import com.audioagent.transcription.config.TranscriptionProperties;
import com.audioagent.transcription.dto.AsrSegmentResponse;
import com.audioagent.transcription.dto.AsrTranscriptionResponse;
import com.audioagent.transcription.exception.TranscriptionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AsrResponseValidator {

    private static final int MAX_FULL_TEXT_LENGTH = 5_000_000;
    private static final int MAX_SEGMENT_TEXT_LENGTH = 65_535;
    private static final long MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long END_TOLERANCE_MS = 2_000;
    private final TranscriptionProperties properties;

    public void validate(AsrTranscriptionResponse response) {
        if (response == null || !StringUtils.hasText(response.getLanguage())
                || !StringUtils.hasText(response.getFullText())
                || response.getFullText().length() > MAX_FULL_TEXT_LENGTH
                || response.getDurationMs() == null
                || response.getDurationMs() <= 0
                || response.getDurationMs() > MAX_DURATION_MS) {
            throw invalid("语音识别服务返回了无效的文字或时长");
        }
        List<AsrSegmentResponse> segments = response.getSegments();
        if (segments == null || segments.isEmpty()
                || segments.size() > properties.getMaxSegmentCount()) {
            throw invalid("语音识别服务返回了无效的片段数量");
        }
        Set<Integer> orders = new HashSet<>();
        long previousStart = -1;
        for (AsrSegmentResponse segment : segments) {
            if (segment == null || segment.getOrder() == null
                    || segment.getOrder() <= 0
                    || !orders.add(segment.getOrder())
                    || segment.getStartMs() == null
                    || segment.getEndMs() == null
                    || segment.getStartMs() < 0
                    || segment.getEndMs() <= segment.getStartMs()
                    || segment.getStartMs() < previousStart
                    || segment.getEndMs()
                    > response.getDurationMs() + END_TOLERANCE_MS
                    || !StringUtils.hasText(segment.getText())
                    || segment.getText().length() > MAX_SEGMENT_TEXT_LENGTH
                    || segment.getSpeaker() != null
                    && segment.getSpeaker().length() > 64
                    || !validConfidence(segment.getConfidence())) {
                throw invalid("语音识别服务返回了无效的时间戳片段");
            }
            previousStart = segment.getStartMs();
        }
    }

    private boolean validConfidence(BigDecimal confidence) {
        return confidence == null
                || confidence.compareTo(BigDecimal.ZERO) >= 0
                && confidence.compareTo(BigDecimal.ONE) <= 0;
    }

    private TranscriptionException invalid(String message) {
        return new TranscriptionException(
                ErrorCode.ASR_RESPONSE_INVALID, false, message);
    }
}
