-- AudioAgent: per-user product preferences
-- MySQL 8 / Navicat. No user IDs are seeded by this migration.

USE audio_agent;

CREATE TABLE IF NOT EXISTS user_setting (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake setting ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    default_denoise_strength VARCHAR(32) NOT NULL,
    processing_strategy VARCHAR(32) NOT NULL,
    auto_limit_peak TINYINT(1) NOT NULL,
    require_step_confirmation TINYINT(1) NOT NULL,
    preserve_playback_position TINYINT(1) NOT NULL,
    issue_context_seconds INT NOT NULL,
    default_playback_volume DECIMAL(5,2) NOT NULL,
    default_page_size INT NOT NULL,
    notify_on_task_complete TINYINT(1) NOT NULL,
    auto_open_result_page TINYINT(1) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_setting_user_id (user_id),
    CONSTRAINT chk_user_setting_denoise CHECK (
        default_denoise_strength IN ('LIGHT', 'MEDIUM')
    ),
    CONSTRAINT chk_user_setting_strategy CHECK (
        processing_strategy IN ('CONSERVATIVE', 'BALANCED')
    ),
    CONSTRAINT chk_user_setting_booleans CHECK (
        auto_limit_peak IN (0, 1)
        AND require_step_confirmation IN (0, 1)
        AND preserve_playback_position IN (0, 1)
        AND notify_on_task_complete IN (0, 1)
        AND auto_open_result_page IN (0, 1)
    ),
    CONSTRAINT chk_user_setting_context CHECK (
        issue_context_seconds BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_user_setting_volume CHECK (
        default_playback_volume BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_user_setting_page_size CHECK (
        default_page_size IN (10, 20, 50)
    ),
    CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One persisted preference set per AudioAgent user';
