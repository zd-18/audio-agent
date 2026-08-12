-- AudioAgent: transcript-grounded multi-turn Agent chat MVP.
-- MySQL 8 / Navicat. Does not modify transcript source data.

USE audio_agent;

CREATE TABLE IF NOT EXISTS agent_conversation (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake conversation ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    transcript_id BIGINT NOT NULL COMMENT 'audio_transcript.id source',
    title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    last_message_id BIGINT NULL,
    last_message_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_agent_conversation_user_updated (user_id, updated_at),
    KEY idx_agent_conversation_user_transcript_updated
        (user_id, transcript_id, updated_at),
    KEY idx_agent_conversation_transcript (transcript_id),
    CONSTRAINT fk_agent_conversation_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_conversation_transcript FOREIGN KEY (transcript_id)
        REFERENCES audio_transcript(id) ON DELETE RESTRICT,
    CONSTRAINT chk_agent_conversation_status CHECK (
        status IN ('ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT chk_agent_conversation_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Transcript-grounded Agent conversations';

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake message ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    conversation_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NULL,
    status VARCHAR(20) NOT NULL,
    reply_to_message_id BIGINT NULL,
    client_request_id VARCHAR(64) NULL,
    model_name VARCHAR(100) NULL,
    prompt_version VARCHAR(100) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_message_sequence (conversation_id, sequence_no),
    UNIQUE KEY uk_agent_message_client_request
        (conversation_id, client_request_id),
    KEY idx_agent_message_user_conversation
        (user_id, conversation_id, sequence_no),
    KEY idx_agent_message_reply (reply_to_message_id),
    CONSTRAINT fk_agent_message_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES agent_conversation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_message_reply FOREIGN KEY (reply_to_message_id)
        REFERENCES agent_message(id) ON DELETE RESTRICT,
    CONSTRAINT chk_agent_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_agent_message_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_agent_message_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_agent_message_prompt_tokens CHECK (
        prompt_tokens IS NULL OR prompt_tokens >= 0
    ),
    CONSTRAINT chk_agent_message_completion_tokens CHECK (
        completion_tokens IS NULL OR completion_tokens >= 0
    ),
    CONSTRAINT chk_agent_message_total_tokens CHECK (
        total_tokens IS NULL OR total_tokens >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent user and assistant messages';

CREATE TABLE IF NOT EXISTS agent_message_citation (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake citation ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    quote VARCHAR(1000) NOT NULL,
    citation_order INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_citation_message_order
        (message_id, citation_order),
    KEY idx_agent_citation_conversation_message
        (conversation_id, message_id),
    KEY idx_agent_citation_transcript_segment
        (transcript_id, segment_id),
    CONSTRAINT fk_agent_citation_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_citation_conversation FOREIGN KEY (conversation_id)
        REFERENCES agent_conversation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_citation_message FOREIGN KEY (message_id)
        REFERENCES agent_message(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_citation_transcript FOREIGN KEY (transcript_id)
        REFERENCES audio_transcript(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_citation_segment FOREIGN KEY (segment_id)
        REFERENCES audio_transcript_segment(id) ON DELETE RESTRICT,
    CONSTRAINT chk_agent_citation_order CHECK (citation_order > 0),
    CONSTRAINT chk_agent_citation_time CHECK (
        start_ms >= 0 AND end_ms >= start_ms
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Auditable citations for Agent assistant messages';
