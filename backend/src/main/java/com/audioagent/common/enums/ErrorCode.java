package com.audioagent.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    PROCESSING_CONFIRMATION_NOT_FOUND(40213, "Processing confirmation not found"),
    PROCESSING_CONFIRMATION_NOT_EDITABLE(40214, "Processing confirmation is not editable"),
    PROCESSING_CONFIRMATION_STALE(40215, "Processing confirmation is stale"),
    PROCESSING_CONFIRMATION_HAS_PENDING_STEPS(40216, "Processing confirmation has pending steps"),
    PROCESSING_STEP_CONFIRMATION_REQUIRED(40217, "Explicit step confirmation is required"),
    PROCESSING_PARAMETER_INVALID(40218, "Processing parameter is invalid"),
    PROCESSING_CONFIRMATION_ALREADY_CONFIRMED(40219, "Processing confirmation is already confirmed"),
    PROCESSING_CONFIRMATION_CANCELLED(40220, "Processing confirmation is cancelled"),
    PROCESSING_EXECUTION_NOT_FOUND(40221, "Processing execution not found"),
    PROCESSING_EXECUTION_CONFIRMATION_NOT_READY(40222, "Processing confirmation is not ready for execution"),
    PROCESSING_EXECUTION_NO_ACCEPTED_STEPS(40223, "Processing confirmation has no accepted steps"),
    PROCESSING_EXECUTION_ALREADY_EXISTS(40224, "Processing execution already exists"),
    PROCESSING_EXECUTION_NOT_RETRYABLE(40225, "Processing execution is not retryable"),
    PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND(40226, "Processing source file not found"),
    PROCESSING_EXECUTION_UNSUPPORTED_OPERATION(40227, "Processing operation is unsupported"),
    PROCESSING_EXECUTION_INVALID_PARAMETER(40228, "Processing execution parameter is invalid"),
    PROCESSING_EXECUTION_FFMPEG_FAILED(40229, "FFmpeg processing failed"),
    PROCESSING_EXECUTION_OUTPUT_INVALID(40230, "Processing output is invalid"),
    PROCESSING_EXECUTION_UPLOAD_FAILED(40231, "Processing output upload failed"),
    PROCESSING_EXECUTION_FAILED(40232, "Audio processing execution failed"),
    MINIO_DOWNLOAD_FAILED(40406, "MinIO object download failed"),

    // 通用
    PARAM_INVALID(40000, "参数校验失败"),

    // 文件相关 40001-40099
    AUDIO_FILE_NOT_FOUND(40001, "文件不存在"),
    AUDIO_FILE_FORMAT_UNSUPPORTED(40002, "当前文件格式不支持"),
    AUDIO_FILE_TOO_LARGE(40003, "文件大小超出限制"),
    AUDIO_FILE_UPLOAD_FAILED(40004, "文件上传失败"),
    AUDIO_FILE_ACCESS_DENIED(40005, "无权访问该文件"),
    AUDIO_FILE_NOT_AVAILABLE(40006, "文件当前不可用"),
    PLAYBACK_URL_GENERATION_FAILED(40007, "播放地址生成失败"),
    MULTIPART_UPLOAD_NOT_FOUND(40008, "上传任务不存在或已过期"),
    MULTIPART_UPLOAD_ACCESS_DENIED(40009, "无权访问该上传任务"),
    MULTIPART_UPLOAD_STATE_INVALID(40010, "上传任务状态不允许当前操作"),
    MULTIPART_CHUNK_INVALID(40011, "上传分片参数不正确"),
    MULTIPART_CHUNKS_INCOMPLETE(40012, "上传分片尚未完整"),
    MULTIPART_HASH_MISMATCH(40013, "文件 SHA-256 校验失败"),
    MULTIPART_UPLOAD_BUSY(40014, "上传任务正在合并"),

    // 任务相关 40101-40199
    AUDIO_TASK_NOT_FOUND(40101, "任务不存在"),
    AUDIO_TASK_ALREADY_RUNNING(40102, "任务已在执行中"),
    AUDIO_TASK_STATUS_INVALID(40103, "任务状态不合法"),
    AUDIO_TASK_CANCELLED(40104, "任务已取消"),

    // 处理相关 40201-40299
    AUDIO_PREPROCESS_FAILED(40201, "音频预处理失败"),
    ASR_SERVICE_UNAVAILABLE(40202, "ASR服务不可用"),
    ASR_TRANSCRIBE_FAILED(40203, "ASR转写失败"),
    AUDIO_ANALYZE_FAILED(40204, "音频分析失败"),
    REPORT_NOT_READY(40205, "报告尚未生成"),
    REPORT_GENERATION_FAILED(40206, "报告生成失败"),
    REPORT_DATA_INCOMPLETE(40207, "报告数据不完整"),
    REPORT_PARSE_FAILED(40208, "报告数据解析失败"),
    PROCESSING_PLAN_NOT_FOUND(40209, "处理方案不存在"),
    PROCESSING_PLAN_NOT_READY(40210, "处理方案尚未就绪"),
    PROCESSING_PLAN_GENERATION_FAILED(40211, "处理方案生成失败"),
    PROCESSING_PLAN_DATA_INCOMPLETE(40212, "处理方案所需数据不完整"),

    // 问题相关 40301-40399
    AUDIO_ISSUE_NOT_FOUND(40301, "问题记录不存在"),
    AUDIO_ISSUE_STATUS_INVALID(40302, "问题状态不合法"),

    // 认证相关 40501-40599
    AUTH_REQUIRED(40501, "请先登录"),
    AUTH_USERNAME_ALREADY_EXISTS(40502, "用户名已存在"),
    AUTH_USERNAME_OR_PASSWORD_INVALID(40503, "用户名或密码错误"),
    AUTH_USER_DISABLED(40504, "用户已被禁用"),
    AUTH_OLD_PASSWORD_INVALID(40505, "旧密码错误"),
    AUTH_PASSWORD_INVALID(40506, "密码不符合安全要求"),
    AUTH_USER_NOT_FOUND(40507, "用户不存在"),

    // 用户设置相关 40601-40699
    USER_SETTING_NOT_FOUND(40601, "用户设置不存在"),
    USER_SETTING_INVALID(40602, "用户设置内容不合法"),
    USER_SETTING_UPDATE_FAILED(40603, "用户设置保存失败，请稍后重试"),
    USER_PROFILE_INVALID(40604, "用户资料内容不合法"),

    // 音频转写相关 40701-40799
    TRANSCRIPTION_TASK_NOT_FOUND(40701, "转写任务不存在"),
    TRANSCRIPTION_ALREADY_RUNNING(40702, "转写任务正在进行中"),
    TRANSCRIPTION_NOT_AVAILABLE(40703, "文字稿暂不可用"),
    TRANSCRIPTION_FAILED(40704, "音频转写失败"),
    TRANSCRIPT_NOT_FOUND(40705, "文字稿不存在"),
    ASR_RESPONSE_INVALID(40706, "语音识别结果格式不正确"),
    ASR_TIMEOUT(40707, "语音识别超时"),
    AUDIO_STANDARDIZATION_FAILED(40708, "音频标准化失败"),
    TRANSCRIPT_PERSISTENCE_FAILED(40709, "文字稿保存失败，请联系管理员"),

    // 智能内容分析相关 40801-40899
    AI_SERVICE_NOT_CONFIGURED(40801, "智能分析服务尚未配置"),
    AI_AUTH_FAILED(40802, "智能分析服务认证失败"),
    AI_BALANCE_INSUFFICIENT(40803, "智能分析服务余额不足"),
    AI_REQUEST_INVALID(40804, "智能分析请求不合法"),
    AI_RATE_LIMITED(40805, "智能分析请求过于频繁，请稍后重试"),
    AI_SERVICE_UNAVAILABLE(40806, "智能分析服务暂时不可用"),
    AI_TIMEOUT(40807, "智能分析服务响应超时"),
    AI_RESPONSE_INVALID(40808, "智能分析结果格式不正确"),
    AI_ANALYSIS_FAILED(40809, "智能内容分析失败"),
    AI_INPUT_TOO_LARGE(40810, "文字稿内容超出智能分析安全限制"),
    AI_TASK_NOT_FOUND(40811, "智能分析任务不存在"),
    AI_RESULT_NOT_FOUND(40812, "智能分析结果不存在"),
    AI_TRANSCRIPT_EMPTY(40813, "文字稿内容为空，无法进行智能分析"),
    AI_TASK_NOT_RETRYABLE(40814, "当前智能分析任务不可重试"),
    AI_RESULT_PERSISTENCE_FAILED(
            40815, "智能分析结果保存失败，请联系管理员"),
    DATABASE_SCHEMA_ERROR(40816, "数据库结构异常，请联系管理员"),
    AI_TASK_DATA_INVALID(40817, "智能分析任务数据不完整"),

    // Transcript Agent chat 40901-40999
    AGENT_CONVERSATION_NOT_FOUND(40901, "Agent conversation not found"),
    AGENT_CONVERSATION_ACCESS_DENIED(40902, "Agent conversation access denied"),
    AGENT_CONVERSATION_STATUS_INVALID(40903, "Agent conversation is not active"),
    AGENT_TRANSCRIPT_NOT_FOUND(40904, "Transcript not found"),
    AGENT_MESSAGE_EMPTY(40905, "Question cannot be empty"),
    AGENT_MESSAGE_TOO_LONG(40906, "Question is too long"),
    AGENT_MESSAGE_DUPLICATE(40907, "Duplicate Agent message request"),
    AGENT_CONTEXT_BUILD_FAILED(40908, "Transcript context could not be built"),
    AGENT_AI_UNAVAILABLE(40909, "Agent service is temporarily unavailable"),
    AGENT_AI_TIMEOUT(40910, "Agent service response timed out"),
    AGENT_RESPONSE_INVALID(40911, "Agent response format is invalid"),
    AGENT_CITATION_INVALID(40912, "Agent citation is invalid"),
    AGENT_WORKFLOW_NOT_FOUND(40913, "Agent processing workflow not found"),
    AGENT_WORKFLOW_STATUS_INVALID(40914, "Agent processing workflow status is invalid"),
    AGENT_PROCESSING_CONTEXT_UNAVAILABLE(40915, "Audio processing context is unavailable"),
    AGENT_PLAN_INVALID(40916, "Agent processing plan is invalid"),
    AGENT_PLAN_FAILED(40917, "Agent processing plan could not be generated"),

   // MinIO相关 40401-40499
MINIO_UPLOAD_FAILED(40401, "MinIO上传失败"),
MINIO_OBJECT_NOT_FOUND(40402, "MinIO对象不存在"),
MINIO_DELETE_FAILED(40403, "MinIO对象删除失败"),
MINIO_PRESIGNED_URL_FAILED(40404, "生成MinIO临时访问地址失败"),
MINIO_OBJECT_CHECK_FAILED(40405, "检查MinIO对象状态失败"),

    // 系统
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
