-- AudioAgent: repair version numbering produced by migration 22.
-- MySQL 8 / Navicat migration. This migration preserves every audio file and
-- all lineage columns; it only rewrites version_no and adds its unique key.

USE audio_agent;

DROP TEMPORARY TABLE IF EXISTS tmp_audio_file_version_numbering;

CREATE TEMPORARY TABLE tmp_audio_file_version_numbering (
    id BIGINT UNSIGNED NOT NULL,
    root_audio_file_id BIGINT UNSIGNED NOT NULL,
    version_no INT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO tmp_audio_file_version_numbering (
    id,
    root_audio_file_id,
    version_no
)
SELECT file.id,
       file.root_audio_file_id,
       ROW_NUMBER() OVER (
           PARTITION BY file.root_audio_file_id
           ORDER BY
               CASE
                   WHEN file.id = file.root_audio_file_id THEN 0
                   ELSE 1
               END,
               file.created_at,
               file.id
       ) - 1 AS version_no
FROM audio_file file
WHERE file.root_audio_file_id IS NOT NULL;

-- Move through distinct negative values first. This also makes the migration
-- safe when the unique key already exists and two rows need new numbers.
UPDATE audio_file AS file
JOIN tmp_audio_file_version_numbering AS numbering
    ON numbering.id = file.id
SET file.version_no = -numbering.version_no - 1;

UPDATE audio_file AS file
JOIN tmp_audio_file_version_numbering AS numbering
    ON numbering.id = file.id
SET file.version_no = numbering.version_no;

DROP TEMPORARY TABLE tmp_audio_file_version_numbering;

DROP PROCEDURE IF EXISTS ensure_audio_file_root_version_key;

DELIMITER $$

CREATE PROCEDURE ensure_audio_file_root_version_key()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = 'audio_agent'
          AND table_name = 'audio_file'
          AND index_name = 'uk_audio_file_root_version'
          AND non_unique = 0
    ) THEN
        ALTER TABLE audio_file
            ADD UNIQUE KEY uk_audio_file_root_version (
                root_audio_file_id,
                version_no
            );
    END IF;
END$$

DELIMITER ;

CALL ensure_audio_file_root_version_key();
DROP PROCEDURE ensure_audio_file_root_version_key;
