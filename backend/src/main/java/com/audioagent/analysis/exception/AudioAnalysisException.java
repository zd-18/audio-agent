package com.audioagent.analysis.exception;

import lombok.Getter;

/**
 * 音频分析异常，明确区分可重试和不可重试场景。
 * 调用方通过 {@link #isRetryable()} 决定是否重试。
 */
@Getter
public class AudioAnalysisException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public AudioAnalysisException(String errorCode,
                                  boolean retryable,
                                  String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AudioAnalysisException(String errorCode,
                                  boolean retryable,
                                  String message,
                                  Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /**
     * 预定义错误码常量。
     */
    public static final class ErrorCodes {

        private ErrorCodes() {
        }

        /** 可重试 */
        public static final String FFPROBE_TIMEOUT = "FFPROBE_TIMEOUT";
        public static final String TEMPORARY_IO_ERROR = "TEMPORARY_IO_ERROR";
        public static final String PROCESS_INTERRUPTED = "PROCESS_INTERRUPTED";
        public static final String TEMPORARY_SERVICE_UNAVAILABLE =
                "TEMPORARY_SERVICE_UNAVAILABLE";
        public static final String RETRY_MESSAGE_PUBLISH_FAILED =
                "RETRY_MESSAGE_PUBLISH_FAILED";
        public static final String SILENCE_DETECT_TIMEOUT =
                "SILENCE_DETECT_TIMEOUT";
        public static final String SILENCE_DETECT_EXECUTION_FAILED =
                "SILENCE_DETECT_EXECUTION_FAILED";
        public static final String LOUDNESS_ANALYSIS_TIMEOUT =
                "LOUDNESS_ANALYSIS_TIMEOUT";
        public static final String LOUDNESS_ANALYSIS_EXECUTION_FAILED =
                "LOUDNESS_ANALYSIS_EXECUTION_FAILED";
        public static final String VOLUME_SEGMENT_ANALYSIS_FAILED =
                "VOLUME_SEGMENT_ANALYSIS_FAILED";
        public static final String NOISE_ANALYSIS_TIMEOUT =
                "NOISE_ANALYSIS_TIMEOUT";
        public static final String NOISE_ANALYSIS_EXECUTION_FAILED =
                "NOISE_ANALYSIS_EXECUTION_FAILED";

        /** 不可重试 */
        public static final String AUDIO_FILE_NOT_FOUND = "AUDIO_FILE_NOT_FOUND";
        public static final String FFPROBE_NOT_FOUND = "FFPROBE_NOT_FOUND";
        public static final String NO_AUDIO_STREAM = "NO_AUDIO_STREAM";
        public static final String INVALID_AUDIO_FILE = "INVALID_AUDIO_FILE";
        public static final String FFPROBE_RESULT_PARSE_ERROR =
                "FFPROBE_RESULT_PARSE_ERROR";
        public static final String INVALID_TASK_MESSAGE = "INVALID_TASK_MESSAGE";
        public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
        public static final String ALREADY_CLAIMED = "ALREADY_CLAIMED";
        public static final String FFMPEG_NOT_FOUND = "FFMPEG_NOT_FOUND";
        public static final String SILENCE_DETECT_PARSE_FAILED =
                "SILENCE_DETECT_PARSE_FAILED";
        public static final String LOUDNESS_ANALYSIS_PARSE_FAILED =
                "LOUDNESS_ANALYSIS_PARSE_FAILED";
        public static final String VOLUME_SEGMENT_PARSE_FAILED =
                "VOLUME_SEGMENT_PARSE_FAILED";
        public static final String NOISE_ANALYSIS_PARSE_FAILED =
                "NOISE_ANALYSIS_PARSE_FAILED";
        public static final String INVALID_CONFIGURATION =
                "INVALID_CONFIGURATION";
        public static final String REPORT_GENERATION_FAILED =
                "REPORT_GENERATION_FAILED";
    }
}
