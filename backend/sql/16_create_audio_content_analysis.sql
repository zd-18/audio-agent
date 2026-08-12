-- AudioAgent: DeepSeek-backed controlled transcript content analysis.
-- MySQL 8 / Navicat. Safe for existing transcription data.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_content_analysis_task (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake task ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    transcript_id BIGINT NOT NULL COMMENT 'audio_transcript.id source',
    status VARCHAR(32) NOT NULL,
    analysis_types JSON NOT NULL,
    summary_style VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    progress_percent INT NOT NULL DEFAULT 0,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_content_analysis_user_status_created
        (user_id, status, created_at),
    KEY idx_content_analysis_user_transcript_created
        (user_id, transcript_id, created_at),
    CONSTRAINT fk_content_analysis_task_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_analysis_task_transcript FOREIGN KEY (transcript_id)
        REFERENCES audio_transcript(id) ON DELETE RESTRICT,
    CONSTRAINT chk_content_analysis_task_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_content_analysis_task_style CHECK (
        summary_style IN ('CONCISE', 'STANDARD', 'DETAILED')
    ),
    CONSTRAINT chk_content_analysis_task_progress CHECK (
        progress_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_content_analysis_task_retry CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Controlled transcript content analysis tasks';

CREATE TABLE IF NOT EXISTS audio_content_analysis_result (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake result ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    task_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    summary_json JSON NOT NULL,
    key_points_json JSON NOT NULL,
    chapters_json JSON NOT NULL,
    speech_issues_json JSON NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_content_analysis_result_task (task_id),
    KEY idx_content_analysis_result_user_transcript
        (user_id, transcript_id),
    CONSTRAINT fk_content_analysis_result_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_analysis_result_task FOREIGN KEY (task_id)
        REFERENCES audio_content_analysis_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_analysis_result_transcript
        FOREIGN KEY (transcript_id)
        REFERENCES audio_transcript(id) ON DELETE RESTRICT,
    CONSTRAINT chk_content_analysis_prompt_tokens CHECK (
        prompt_tokens IS NULL OR prompt_tokens >= 0
    ),
    CONSTRAINT chk_content_analysis_completion_tokens CHECK (
        completion_tokens IS NULL OR completion_tokens >= 0
    ),
    CONSTRAINT chk_content_analysis_total_tokens CHECK (
        total_tokens IS NULL OR total_tokens >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Validated final transcript content analysis results';
