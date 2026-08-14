package com.audioagent.analysis.processing;

import org.springframework.stereotype.Component;

@Component
public class ProcessingOperationCatalog {

    public String title(ProcessingOperationType type) {
        return switch (type) {
            case NORMALIZE_VOLUME -> "统一整段音量";
            case TRIM_SEGMENT -> "裁剪指定音频片段";
            case DENOISE -> "智能降噪";
            case REVIEW_SILENCE -> "试听静音片段";
            case TRIM_SILENCE -> "缩短较长静音";
            case INCREASE_GAIN -> "提升局部音量";
            case DECREASE_GAIN -> "降低突发音量";
            case DENOISE_REVIEW -> "检查疑似背景噪声";
            case NORMALIZE_LOUDNESS -> "统一整体响度";
            case LIMIT_PEAK -> "控制过高峰值";
        };
    }

    public String description(ProcessingOperationType type,
                              Long startMs, Long endMs) {
        return switch (type) {
            case NORMALIZE_VOLUME ->
                    "对整段音频执行稳定的响度标准化，并按目标真峰值限制过高峰值。";
            case TRIM_SEGMENT -> "裁剪" + range(startMs, endMs)
                    + "，该时间范围将从结果音频中移除。";
            case DENOISE ->
                    "降低整段音频中持续的背景噪声（如电流声、环境底噪），保留主要内容。";
            case REVIEW_SILENCE -> "建议试听" + range(startMs, endMs)
                    + "，确认该静音是否需要处理。";
            case TRIM_SILENCE -> "建议试听" + range(startMs, endMs)
                    + "，确认后删除或缩短该静音片段。";
            case INCREASE_GAIN ->
                    "建议适当提升该片段音量，使其与前后内容保持一致。";
            case DECREASE_GAIN ->
                    "建议降低该片段增益，并检查是否存在失真。";
            case DENOISE_REVIEW ->
                    "建议先试听确认，再考虑进行轻度降噪。";
            case NORMALIZE_LOUDNESS ->
                    "建议统一整段音频的整体响度，改善不同设备上的听感。";
            case LIMIT_PEAK ->
                    "建议限制过高峰值，降低削波和失真风险。";
        };
    }

    public String reason(ProcessingOperationType type) {
        return switch (type) {
            case NORMALIZE_VOLUME ->
                    "整段音频响度或峰值偏离建议范围，需要统一音量并控制真峰值。";
            case TRIM_SEGMENT ->
                    "该时间范围需要从结果音频中裁剪。";
            case DENOISE ->
                    "检测到持续的背景噪声，可能影响内容听感，建议进行智能降噪。";
            case REVIEW_SILENCE ->
                    "该片段存在短时静音，建议结合内容语义确认。";
            case TRIM_SILENCE ->
                    "该片段持续静音时间较长，可能影响内容节奏。";
            case INCREASE_GAIN ->
                    "该片段音量明显低于整体水平，可能影响听清内容。";
            case DECREASE_GAIN ->
                    "该片段音量明显高于整体水平，可能造成突兀听感。";
            case DENOISE_REVIEW ->
                    "该片段存在疑似背景噪声，但需要试听后再决定是否处理。";
            case NORMALIZE_LOUDNESS ->
                    "整体响度偏离建议目标，可考虑统一调整。";
            case LIMIT_PEAK ->
                    "检测到过高峰值风险，建议控制峰值以减少失真可能。";
        };
    }

    private String range(Long startMs, Long endMs) {
        if (startMs == null || endMs == null) {
            return "该片段";
        }
        return " " + format(startMs) + " 至 " + format(endMs);
    }

    private String format(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
