-- AudioAgent: audio-only processing conversations
-- MySQL 8 / Navicat migration. Does not delete or modify historical rows;
-- existing transcript-grounded conversations keep their transcript_id.

USE audio_agent;

ALTER TABLE agent_conversation
    MODIFY COLUMN transcript_id BIGINT NULL
        COMMENT 'audio_transcript.id source; NULL for audio-only processing conversations',
    ADD COLUMN audio_file_id BIGINT UNSIGNED NULL
        COMMENT 'audio_file.id source for audio-only processing conversations'
        AFTER transcript_id,
    ADD KEY idx_agent_conversation_audio_file (audio_file_id),
    ADD KEY idx_agent_conversation_user_audio_updated
        (user_id, audio_file_id, updated_at),
    ADD CONSTRAINT fk_agent_conversation_audio_file FOREIGN KEY (audio_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT;
