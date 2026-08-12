-- ============================================================
-- 音频质量诊断平台 - 音频分析任务表
-- 版本：v1.0
-- ============================================================

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_analysis_task (
    id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    audio_file_id BIGINT UNSIGNED NOT NULL COMMENT '音频文件ID',

    analysis_type VARCHAR(32) NOT NULL DEFAULT 'FULL' COMMENT
        '分析类型：FULL 完整分析',

    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT
        '状态：PENDING 待处理、PROCESSING 处理中、SUCCESS 成功、FAILED 失败',

    progress INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',

    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '完成时间',

    PRIMARY KEY (id),
    KEY idx_audio_file (audio_file_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频分析任务表';
