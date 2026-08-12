package com.audioagent.analysis.vo;

import com.audioagent.analysis.entity.AudioAnalysisResult;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AudioAnalysisResultVO {

    private String formatName;

    private String codecName;

    private Long durationMs;

    private Integer sampleRate;

    private Integer channels;

    private Long bitRate;

    private Long fileSize;

    private Integer issueCount;

    private Integer silenceCount;

    private Long totalSilenceDurationMs;

    private BigDecimal silenceRatio;

    private LoudnessVO loudness;

    public static AudioAnalysisResultVO from(
            AudioAnalysisResult result,
            com.audioagent.analysis.loudness.LoudnessEvaluator evaluator) {
        if (result == null) {
            return null;
        }
        return AudioAnalysisResultVO.builder()
                .formatName(result.getFormatName())
                .codecName(result.getCodecName())
                .durationMs(result.getDurationMs())
                .sampleRate(result.getSampleRate())
                .channels(result.getChannels())
                .bitRate(result.getBitRate())
                .fileSize(result.getFileSize())
                .issueCount(result.getIssueCount())
                .silenceCount(result.getSilenceCount())
                .totalSilenceDurationMs(
                        result.getTotalSilenceDurationMs())
                .silenceRatio(result.getSilenceRatio())
                .loudness(LoudnessVO.from(result, evaluator))
                .build();
    }
}
