-- ============================================================
-- 音频质量诊断平台 - audio_analysis_task 增加重试字段
-- 版本：v1.0
-- ============================================================

USE audio_agent;

ALTER TABLE audio_analysis_task
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    ADD COLUMN max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    ADD COLUMN next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    ADD COLUMN last_error_code VARCHAR(64) NULL COMMENT '最后一次错误码',
    ADD COLUMN last_message_id VARCHAR(128) NULL COMMENT '最近一次消息ID';
