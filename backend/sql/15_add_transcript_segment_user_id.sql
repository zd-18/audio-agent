-- AudioAgent: align audio_transcript_segment with the multi-user transcript model.
-- MySQL 8 / Navicat. Safe to execute repeatedly.
--
-- This is an incremental migration for databases where the legacy
-- audio_transcript_segment table already existed before migration 14 ran.
-- It does not delete data and does not rebuild the table.

USE audio_agent;

SET @schema_name = DATABASE();

-- 1. Add the ownership snapshot as nullable first so existing rows remain valid.
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audio_transcript_segment ADD COLUMN user_id BIGINT NULL COMMENT ''所属用户ID'' AFTER id',
        'SELECT ''audio_transcript_segment.user_id already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_transcript_segment'
      AND COLUMN_NAME = 'user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Backfill legacy rows from the owning transcript. Orphan rows are retained.
UPDATE audio_transcript_segment AS segment
INNER JOIN audio_transcript AS transcript
        ON transcript.id = segment.transcript_id
SET segment.user_id = transcript.user_id
WHERE segment.user_id IS NULL;

-- 3. Report any orphan rows that could not be backfilled. The NOT NULL ALTER
--    below intentionally fails while this count is non-zero. No rows are
--    deleted; repair the missing transcript relationship and rerun.
SET @remaining_null_user_ids = (
    SELECT COUNT(*)
    FROM audio_transcript_segment
    WHERE user_id IS NULL
);
SELECT @remaining_null_user_ids AS remaining_unowned_segment_count;

-- 4. Enforce the entity contract only after every existing row has an owner.
SET @ddl = (
    SELECT IF(
        IS_NULLABLE = 'YES' OR COLUMN_TYPE <> 'bigint',
        'ALTER TABLE audio_transcript_segment MODIFY COLUMN user_id BIGINT NOT NULL COMMENT ''所属用户ID''',
        'SELECT ''audio_transcript_segment.user_id already BIGINT NOT NULL''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_transcript_segment'
      AND COLUMN_NAME = 'user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Add the ownership lookup index only when no index already starts with the
--    same (user_id, transcript_id) column pair. This avoids duplicate indexes
--    when migration 14 created idx_transcript_segment_user.
SET @has_owner_transcript_index = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 'audio_transcript_segment'
        GROUP BY INDEX_NAME
        HAVING GROUP_CONCAT(
                   COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','
               ) LIKE 'user_id,transcript_id%'
    ) AS matching_indexes
);
SET @ddl = IF(
    @has_owner_transcript_index > 0,
    'SELECT ''audio_transcript_segment ownership index already exists''',
    'CREATE INDEX idx_transcript_segment_user_transcript ON audio_transcript_segment(user_id, transcript_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
