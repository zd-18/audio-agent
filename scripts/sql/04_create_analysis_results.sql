-- ============================================================
-- 音频质量诊断平台 - 音频分析结果表
-- 版本：v1.0
-- ============================================================

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_analysis_result (
    id BIGINT NOT NULL COMMENT '结果ID',
    task_id BIGINT NOT NULL COMMENT '分析任务ID',
    audio_file_id BIGINT NOT NULL COMMENT '音频文件ID',

    format_name VARCHAR(128) NULL COMMENT '封装格式',
    codec_name VARCHAR(128) NULL COMMENT '音频编码',
    duration_ms BIGINT NULL COMMENT '时长，毫秒',
    sample_rate INT NULL COMMENT '采样率',
    channels INT NULL COMMENT '声道数',
    bit_rate BIGINT NULL COMMENT '码率',
    file_size BIGINT NULL COMMENT '文件大小',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_audio_file_id (audio_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频分析结果表';
