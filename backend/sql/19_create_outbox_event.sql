-- AudioAgent: transactional outbox infrastructure.
-- MySQL 8 / Navicat. Events are retained for audit and failure analysis.

USE audio_agent;

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake event ID and idempotency key',
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSON NOT NULL COMMENT 'JSON only; credentials and tokens are forbidden',
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    locked_at DATETIME(3) NULL,
    lock_owner VARCHAR(64) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_aggregate_event (
        aggregate_type, aggregate_id, event_type
    ),
    KEY idx_outbox_pending (status, next_retry_at, created_at),
    KEY idx_outbox_stale_lock (status, locked_at),
    CONSTRAINT chk_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Transactional outbox events retained for delivery audit';
