-- AudioAgent: silence issue segments and analysis summary
-- Run against the audio_agent database in Navicat.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_issue_segment (
    id BIGINT NOT NULL COMMENT 'Issue segment ID',
    task_id BIGINT NOT NULL COMMENT 'audio_analysis_task.id',
    audio_file_id BIGINT NOT NULL COMMENT 'audio_file.id',
    issue_type VARCHAR(32) NOT NULL
        COMMENT 'SILENCE/VOLUME_DROP/VOLUME_SPIKE/NOISE_RISK',
    start_ms BIGINT NOT NULL COMMENT 'Inclusive start in milliseconds',
    end_ms BIGINT NOT NULL COMMENT 'Exclusive end in milliseconds',
    duration_ms BIGINT NOT NULL COMMENT 'Validated end_ms - start_ms',
    severity VARCHAR(16) NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    metric_json JSON NULL COMMENT 'Detector configuration snapshot',
    description VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_task_type_range
        (task_id, issue_type, start_ms, end_ms),
    KEY idx_issue_task_id (task_id),
    KEY idx_issue_audio_file_id (audio_file_id),
    KEY idx_issue_type (issue_type),
    KEY idx_issue_task_type (task_id, issue_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Extensible audio analysis issue segments';

ALTER TABLE audio_analysis_result
    ADD COLUMN issue_count INT NOT NULL DEFAULT 0
        COMMENT 'All detected issue segments',
    ADD COLUMN silence_count INT NOT NULL DEFAULT 0
        COMMENT 'Detected silence segments',
    ADD COLUMN total_silence_duration_ms BIGINT NOT NULL DEFAULT 0
        COMMENT 'Total silence duration in milliseconds',
    ADD COLUMN silence_ratio DECIMAL(12,8) NOT NULL DEFAULT 0
        COMMENT 'total_silence_duration_ms / duration_ms';
