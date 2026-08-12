-- AudioAgent: align the legacy transcript speaker column with the Java model.
-- MySQL 8 / Navicat. Safe to execute repeatedly.
--
-- Legacy databases used audio_transcript_segment.speaker, while migration 14
-- and AudioTranscriptSegment use speaker_label. This migration preserves the
-- existing values and never drops the segment table or transcript data.

USE audio_agent;

SET @schema_name = DATABASE();

SET @has_speaker = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_transcript_segment'
      AND COLUMN_NAME = 'speaker'
);

SET @has_speaker_label = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_transcript_segment'
      AND COLUMN_NAME = 'speaker_label'
);

-- Rename the legacy column when it is the only speaker column. If neither
-- column exists, add the canonical nullable column used by the entity.
SET @ddl = CASE
    WHEN @has_speaker = 1 AND @has_speaker_label = 0 THEN
        'ALTER TABLE audio_transcript_segment CHANGE COLUMN speaker speaker_label VARCHAR(64) NULL COMMENT ''说话人标签'''
    WHEN @has_speaker = 0 AND @has_speaker_label = 0 THEN
        'ALTER TABLE audio_transcript_segment ADD COLUMN speaker_label VARCHAR(64) NULL COMMENT ''说话人标签'' AFTER end_ms'
    ELSE
        'SELECT ''audio_transcript_segment.speaker_label already exists'''
END;
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Some transitional databases can contain both columns. Copy only missing
-- canonical values and retain the legacy column so no data is discarded.
SET @dml = IF(
    @has_speaker = 1 AND @has_speaker_label = 1,
    'UPDATE audio_transcript_segment SET speaker_label = speaker WHERE speaker_label IS NULL AND speaker IS NOT NULL',
    'SELECT ''no transcript speaker backfill required'''
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 'audio_transcript_segment'
  AND COLUMN_NAME IN ('speaker', 'speaker_label')
ORDER BY ORDINAL_POSITION;
