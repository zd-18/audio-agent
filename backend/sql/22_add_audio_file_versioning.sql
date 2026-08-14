-- AudioAgent: non-destructive audio version lineage
-- MySQL 8 / Navicat migration. This migration does not delete audio files.

USE audio_agent;

ALTER TABLE audio_file
    ADD COLUMN root_audio_file_id BIGINT UNSIGNED NULL
        COMMENT 'Root ORIGINAL audio_file.id' AFTER source_file_id,
    ADD COLUMN version_no INT NOT NULL DEFAULT 0
        COMMENT 'Monotonic version number within one root lineage'
        AFTER root_audio_file_id,
    ADD COLUMN version_summary VARCHAR(200) NULL
        COMMENT 'Short user-facing processing summary' AFTER version_no,
    ADD COLUMN source_execution_id BIGINT NULL
        COMMENT 'Execution that created this version; NULL for roots'
        AFTER version_summary;

WITH RECURSIVE version_tree AS (
    SELECT id, id AS root_id, 0 AS depth
    FROM audio_file
    WHERE source_file_id IS NULL

    UNION ALL

    SELECT child.id, tree.root_id, tree.depth + 1
    FROM audio_file child
    JOIN version_tree tree ON child.source_file_id = tree.id
    WHERE tree.depth < 1000
)
UPDATE audio_file file
JOIN version_tree tree ON tree.id = file.id
SET file.root_audio_file_id = tree.root_id,
    file.version_no = tree.depth,
    file.version_summary = COALESCE(
        file.version_summary,
        CASE WHEN tree.depth = 0 THEN '原始版本' ELSE '历史处理版本' END
    );

ALTER TABLE audio_file
    ADD KEY idx_audio_file_version_chain (
        user_id, root_audio_file_id, version_no, created_at
    ),
    ADD UNIQUE KEY uk_audio_file_source_execution (source_execution_id);
