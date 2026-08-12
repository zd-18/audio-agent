-- AudioAgent: user confirmation of processing-plan suggestions
-- MySQL 8 / Navicat migration. Does not modify audio data.

USE audio_agent;

-- Add the per-task regeneration revision to an existing installation.
SET @plan_revision_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'audio_processing_plan'
      AND COLUMN_NAME = 'plan_revision'
);
SET @plan_revision_ddl = IF(
    @plan_revision_exists = 0,
    'ALTER TABLE audio_processing_plan ADD COLUMN plan_revision INT NOT NULL DEFAULT 1 COMMENT ''Monotonic regeneration revision for this task'' AFTER plan_version',
    'SELECT ''audio_processing_plan.plan_revision already exists'''
);
PREPARE plan_revision_stmt FROM @plan_revision_ddl;
EXECUTE plan_revision_stmt;
DEALLOCATE PREPARE plan_revision_stmt;

CREATE TABLE IF NOT EXISTS audio_processing_confirmation (
    id BIGINT NOT NULL COMMENT 'User confirmation ID',
    task_id BIGINT NOT NULL COMMENT 'audio_analysis_task.id',
    audio_file_id BIGINT NOT NULL COMMENT 'audio_file.id',
    plan_id BIGINT NOT NULL COMMENT 'audio_processing_plan.id',
    source_plan_revision INT NOT NULL COMMENT 'Plan revision being confirmed',
    confirmation_status VARCHAR(16) NOT NULL COMMENT 'DRAFT/CONFIRMED/STALE/CANCELLED',
    accepted_step_count INT NOT NULL DEFAULT 0,
    rejected_step_count INT NOT NULL DEFAULT 0,
    pending_step_count INT NOT NULL DEFAULT 0,
    confirmation_json JSON NULL COMMENT 'Immutable final decision snapshot',
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_confirmation_plan_revision (plan_id, source_plan_revision),
    KEY idx_confirmation_task_id (task_id),
    KEY idx_confirmation_audio_file_id (audio_file_id),
    KEY idx_confirmation_status (confirmation_status),
    CONSTRAINT chk_confirmation_status CHECK (
        confirmation_status IN ('DRAFT', 'CONFIRMED', 'STALE', 'CANCELLED')
    ),
    CONSTRAINT chk_confirmation_counts CHECK (
        accepted_step_count >= 0 AND rejected_step_count >= 0
        AND pending_step_count >= 0
    ),
    CONSTRAINT fk_confirmation_plan FOREIGN KEY (plan_id)
        REFERENCES audio_processing_plan(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='User decisions for one immutable processing-plan revision';

CREATE TABLE IF NOT EXISTS audio_processing_step_confirmation (
    id BIGINT NOT NULL COMMENT 'Step confirmation ID',
    confirmation_id BIGINT NOT NULL COMMENT 'audio_processing_confirmation.id',
    source_step_id BIGINT NOT NULL COMMENT 'Source processing step ID snapshot reference',
    decision VARCHAR(16) NOT NULL COMMENT 'PENDING/ACCEPTED/REJECTED',
    user_confirmed TINYINT(1) NOT NULL DEFAULT 0,
    parameter_overrides_json JSON NULL COMMENT 'Only fields changed by user',
    effective_parameters_json JSON NULL COMMENT 'Original parameters merged with overrides',
    user_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_confirmation_source_step (confirmation_id, source_step_id),
    KEY idx_step_confirmation_id (confirmation_id),
    KEY idx_step_confirmation_decision (decision),
    KEY idx_step_confirmation_source_step (source_step_id),
    CONSTRAINT chk_step_confirmation_decision CHECK (
        decision IN ('PENDING', 'ACCEPTED', 'REJECTED')
    ),
    CONSTRAINT fk_step_confirmation_parent FOREIGN KEY (confirmation_id)
        REFERENCES audio_processing_confirmation(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Per-step user decisions and effective parameter snapshots';

