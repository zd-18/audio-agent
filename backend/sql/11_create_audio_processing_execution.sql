-- AudioAgent: confirmed processing execution and immutable execution steps
-- MySQL 8 / Navicat migration. This migration never updates source audio.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_processing_execution (
    id BIGINT NOT NULL COMMENT 'Processing execution ID',
    user_id BIGINT NOT NULL COMMENT 'Owner user ID snapshot',
    task_id BIGINT NOT NULL COMMENT 'audio_analysis_task.id',
    audio_file_id BIGINT UNSIGNED NOT NULL COMMENT 'Source audio_file.id',
    confirmation_id BIGINT NOT NULL COMMENT 'Confirmed snapshot identity',
    source_plan_id BIGINT NOT NULL COMMENT 'Confirmed processing plan ID',
    source_plan_revision INT NOT NULL COMMENT 'Confirmed plan revision',
    execution_status VARCHAR(20) NOT NULL
        COMMENT 'PENDING/QUEUED/PROCESSING/SUCCESS/FAILED/CANCELLED/DEAD_LETTER',
    accepted_step_count INT NOT NULL,
    executable_step_count INT NOT NULL,
    skipped_step_count INT NOT NULL DEFAULT 0,
    current_stage VARCHAR(32) NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    result_file_id BIGINT UNSIGNED NULL COMMENT 'New REPAIR_RESULT audio_file.id',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_confirmation_id (confirmation_id),
    KEY idx_execution_task_id (task_id),
    KEY idx_execution_audio_file_id (audio_file_id),
    KEY idx_execution_status (execution_status),
    KEY idx_execution_result_file_id (result_file_id),
    CONSTRAINT chk_processing_execution_status CHECK (
        execution_status IN (
            'PENDING', 'QUEUED', 'PROCESSING', 'SUCCESS', 'FAILED',
            'CANCELLED', 'DEAD_LETTER'
        )
    ),
    CONSTRAINT chk_processing_execution_counts CHECK (
        accepted_step_count > 0
        AND executable_step_count >= 0
        AND skipped_step_count >= 0
        AND executable_step_count + skipped_step_count = accepted_step_count
    ),
    CONSTRAINT chk_processing_execution_progress CHECK (
        progress_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_processing_execution_retry CHECK (
        retry_count >= 0 AND max_retry_count > 0
    ),
    CONSTRAINT fk_processing_execution_task FOREIGN KEY (task_id)
        REFERENCES audio_analysis_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_execution_source_file FOREIGN KEY (audio_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_execution_confirmation FOREIGN KEY (confirmation_id)
        REFERENCES audio_processing_confirmation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_execution_plan FOREIGN KEY (source_plan_id)
        REFERENCES audio_processing_plan(id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_execution_result_file FOREIGN KEY (result_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Asynchronous execution of one immutable confirmed processing snapshot';

CREATE TABLE IF NOT EXISTS audio_processing_execution_step (
    id BIGINT NOT NULL COMMENT 'Execution step ID',
    execution_id BIGINT NOT NULL COMMENT 'audio_processing_execution.id',
    source_step_confirmation_id BIGINT NOT NULL
        COMMENT 'Confirmed decision snapshot source',
    source_processing_step_id BIGINT NOT NULL
        COMMENT 'Original plan step identity for audit only',
    step_order INT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    execution_status VARCHAR(20) NOT NULL
        COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/SKIPPED',
    start_ms BIGINT NULL,
    end_ms BIGINT NULL,
    effective_parameters_json JSON NULL
        COMMENT 'Immutable parameters used for this execution',
    skip_reason VARCHAR(128) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_source_confirmation_step (
        execution_id, source_step_confirmation_id
    ),
    KEY idx_execution_step_execution_id (execution_id),
    KEY idx_execution_step_status (execution_status),
    CONSTRAINT chk_processing_execution_step_status CHECK (
        execution_status IN (
            'PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'SKIPPED'
        )
    ),
    CONSTRAINT chk_processing_execution_step_range CHECK (
        (start_ms IS NULL AND end_ms IS NULL)
        OR (start_ms >= 0 AND end_ms > start_ms)
    ),
    CONSTRAINT fk_processing_execution_step_parent FOREIGN KEY (execution_id)
        REFERENCES audio_processing_execution(id) ON DELETE RESTRICT,
    CONSTRAINT fk_processing_execution_step_confirmation
        FOREIGN KEY (source_step_confirmation_id)
        REFERENCES audio_processing_step_confirmation(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Immutable accepted processing steps and their execution outcome';

-- source_processing_step_id is audit data, not a foreign key: regenerating a
-- plan deletes its old audio_processing_step rows, while confirmed snapshots
-- and their executions must remain independently executable and queryable.
