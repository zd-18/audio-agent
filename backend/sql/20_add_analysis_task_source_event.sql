-- Idempotency key for analysis tasks created from outbox events.

USE audio_agent;

ALTER TABLE audio_analysis_task
    ADD COLUMN source_event_id BIGINT NULL
        COMMENT 'outbox_event.id that created this task' AFTER audio_file_id,
    ADD UNIQUE KEY uk_analysis_task_source_event (source_event_id);
