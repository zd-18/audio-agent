-- AudioAgent: current user-facing audio processing plan and executable steps
-- MySQL 8 / Navicat. Safe to execute repeatedly.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_processing_plan (
    id BIGINT NOT NULL COMMENT 'Processing plan ID',
    task_id BIGINT NOT NULL COMMENT 'audio_analysis_task.id',
    audio_file_id BIGINT NOT NULL COMMENT 'audio_file.id',
    plan_version INT NOT NULL COMMENT 'Configured rule/schema version',
    plan_revision INT NOT NULL DEFAULT 1
        COMMENT 'Monotonic regeneration revision for this task',
    plan_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/READY/INVALID',
    summary VARCHAR(500) NULL COMMENT 'User-facing deterministic summary',
    step_count INT NOT NULL DEFAULT 0 COMMENT 'Current step count',
    estimated_output_duration_ms BIGINT NULL
        COMMENT 'Suggestion-only duration estimate after silence trimming',
    plan_json JSON NOT NULL COMMENT 'Final user-facing plan snapshot',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_processing_plan_task_id (task_id),
    KEY idx_plan_audio_file_id (audio_file_id),
    KEY idx_plan_status (plan_status),
    CONSTRAINT chk_processing_plan_status
        CHECK (plan_status IN ('DRAFT', 'READY', 'INVALID')),
    CONSTRAINT chk_processing_plan_step_count
        CHECK (step_count >= 0),
    CONSTRAINT chk_processing_plan_duration
        CHECK (estimated_output_duration_ms IS NULL
            OR estimated_output_duration_ms >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Current processing proposal for one completed analysis task';

CREATE TABLE IF NOT EXISTS audio_processing_step (
    id BIGINT NOT NULL COMMENT 'Processing step ID',
    plan_id BIGINT NOT NULL COMMENT 'audio_processing_plan.id',
    step_order INT NOT NULL COMMENT 'Continuous order starting at 1',
    operation_type VARCHAR(32) NOT NULL
        COMMENT 'User-facing operation type',
    title VARCHAR(100) NOT NULL COMMENT 'User-facing title snapshot',
    description VARCHAR(500) NOT NULL
        COMMENT 'User-facing description snapshot',
    source_issue_id BIGINT NULL COMMENT 'Primary source issue ID',
    start_ms BIGINT NULL COMMENT 'Null for whole-audio operation',
    end_ms BIGINT NULL COMMENT 'Null for whole-audio operation',
    priority VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
    risk_level VARCHAR(16) NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
    requires_confirmation TINYINT(1) NOT NULL DEFAULT 1,
    parameters_json JSON NULL COMMENT 'Structured suggestion parameters',
    reason VARCHAR(500) NULL COMMENT 'User-facing reason snapshot',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_step_order (plan_id, step_order),
    KEY idx_step_plan_id (plan_id),
    KEY idx_step_source_issue_id (source_issue_id),
    CONSTRAINT chk_processing_step_order CHECK (step_order > 0),
    CONSTRAINT chk_processing_step_priority
        CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_processing_step_risk
        CHECK (risk_level IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_processing_step_range
        CHECK ((start_ms IS NULL AND end_ms IS NULL)
            OR (start_ms IS NOT NULL AND end_ms IS NOT NULL
                AND start_ms >= 0 AND end_ms >= start_ms)),
    CONSTRAINT fk_processing_step_plan
        FOREIGN KEY (plan_id) REFERENCES audio_processing_plan(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Ordered processing proposal steps for future execution tracking';
