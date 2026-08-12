-- AudioAgent: asynchronous audio transcription tasks and transcripts
-- MySQL 8 / Navicat. This migration does not modify audio_file records.

USE audio_agent;

CREATE TABLE IF NOT EXISTS audio_transcription_task (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake task ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    audio_file_id BIGINT UNSIGNED NOT NULL COMMENT 'audio_file.id source',
    status VARCHAR(32) NOT NULL,
    language VARCHAR(32) NOT NULL,
    enable_speaker_diarization TINYINT(1) NOT NULL DEFAULT 0,
    progress_percent INT NOT NULL DEFAULT 0,
    provider VARCHAR(64) NULL,
    model_name VARCHAR(100) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_transcription_task_user_status_created
        (user_id, status, created_at),
    KEY idx_transcription_task_user_file_status_created
        (user_id, audio_file_id, status, created_at),
    KEY idx_transcription_task_file_created
        (audio_file_id, created_at),
    CONSTRAINT fk_transcription_task_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transcription_task_audio_file FOREIGN KEY (audio_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT,
    CONSTRAINT chk_transcription_task_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_transcription_task_diarization CHECK (
        enable_speaker_diarization IN (0, 1)
    ),
    CONSTRAINT chk_transcription_task_progress CHECK (
        progress_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_transcription_task_retry CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Asynchronous audio transcription tasks';

CREATE TABLE IF NOT EXISTS audio_transcript (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake transcript ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    audio_file_id BIGINT UNSIGNED NOT NULL COMMENT 'audio_file.id source',
    transcription_task_id BIGINT NOT NULL,
    language VARCHAR(32) NOT NULL,
    full_text LONGTEXT NOT NULL,
    duration_ms BIGINT NULL,
    speaker_count INT NULL,
    segment_count INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_transcript_task (transcription_task_id),
    KEY idx_transcript_user_file (user_id, audio_file_id),
    KEY idx_transcript_created (created_at),
    CONSTRAINT fk_transcript_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transcript_audio_file FOREIGN KEY (audio_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transcript_task FOREIGN KEY (transcription_task_id)
        REFERENCES audio_transcription_task(id) ON DELETE RESTRICT,
    CONSTRAINT chk_transcript_duration CHECK (
        duration_ms IS NULL OR duration_ms >= 0
    ),
    CONSTRAINT chk_transcript_speaker_count CHECK (
        speaker_count IS NULL OR speaker_count >= 0
    ),
    CONSTRAINT chk_transcript_segment_count CHECK (segment_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Full text produced by one successful transcription task';

CREATE TABLE IF NOT EXISTS audio_transcript_segment (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake segment ID',
    user_id BIGINT NOT NULL COMMENT 'app_user.id owner',
    transcript_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    speaker_label VARCHAR(64) NULL,
    text TEXT NOT NULL,
    confidence DECIMAL(6,5) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_transcript_segment_order (transcript_id, segment_order),
    KEY idx_transcript_segment_user (user_id, transcript_id),
    CONSTRAINT fk_transcript_segment_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transcript_segment_transcript FOREIGN KEY (transcript_id)
        REFERENCES audio_transcript(id) ON DELETE CASCADE,
    CONSTRAINT chk_transcript_segment_order CHECK (segment_order > 0),
    CONSTRAINT chk_transcript_segment_time CHECK (
        start_ms >= 0 AND end_ms > start_ms
    ),
    CONSTRAINT chk_transcript_segment_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Timestamped transcript segments';
