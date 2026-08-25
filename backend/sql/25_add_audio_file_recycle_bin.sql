-- AudioAgent: recoverable audio-file recycle bin.
-- Deleted metadata is retained because analysis, transcription and processing
-- history reference audio_file rows with restrictive foreign keys.

USE audio_agent;

ALTER TABLE audio_file
    MODIFY COLUMN file_status TINYINT NOT NULL DEFAULT 1
        COMMENT '1上传中 2可用 3处理中 4失败 5已删除 6已归档',
    ADD COLUMN pre_delete_status TINYINT NULL
        COMMENT 'file_status before moving to recycle bin' AFTER file_status,
    ADD COLUMN deleted_at DATETIME(3) NULL
        COMMENT 'time moved to recycle bin' AFTER updated_at,
    ADD COLUMN purged_at DATETIME(3) NULL
        COMMENT 'time MinIO object was permanently removed' AFTER deleted_at,
    ADD KEY idx_audio_file_user_deleted
        (user_id, deleted, purged_at, deleted_at);
