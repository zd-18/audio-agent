-- AudioAgent application users and authentication identities
-- MySQL 8 / Navicat migration. Users are created through the register API.

USE audio_agent;

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake user ID',
    username VARCHAR(64) NOT NULL COMMENT 'Unique login name',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt password hash',
    display_name VARCHAR(100) NOT NULL COMMENT 'Display name',
    avatar_url VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL COMMENT 'ACTIVE/DISABLED',
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    KEY idx_app_user_status (status),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_app_user_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AudioAgent login users';
