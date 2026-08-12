-- AudioAgent: persisted user-facing analysis report snapshot
-- MySQL 8 / Navicat. Safe to execute repeatedly.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_analysis_report (
    id BIGINT NOT NULL COMMENT 'Report ID',
    task_id BIGINT NOT NULL COMMENT 'audio_analysis_task.id',
    audio_file_id BIGINT NOT NULL COMMENT 'audio_file.id',
    report_version VARCHAR(16) NOT NULL COMMENT 'Report schema/rule version',
    quality_score INT NOT NULL COMMENT 'Configurable heuristic score 0-100',
    quality_grade VARCHAR(16) NOT NULL
        COMMENT 'EXCELLENT/GOOD/FAIR/POOR',
    summary VARCHAR(500) NULL COMMENT 'User-facing rule-based summary',
    report_json JSON NOT NULL
        COMMENT 'Final user report content; excludes frame-level data',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_report_task_id (task_id),
    KEY idx_report_audio_file_id (audio_file_id),
    KEY idx_report_quality_grade (quality_grade),
    CONSTRAINT chk_report_quality_score
        CHECK (quality_score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Current user-facing audio analysis report snapshot';
