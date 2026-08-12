-- AudioAgent: whole-file loudness metrics
-- Safe to run repeatedly in MySQL 8 / Navicat.

USE audio_agent;

SET @schema_name = DATABASE();

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audio_analysis_result ADD COLUMN integrated_loudness_lufs DECIMAL(8,2) NULL COMMENT ''Integrated loudness in LUFS'' AFTER silence_ratio',
        'SELECT ''integrated_loudness_lufs already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_analysis_result'
      AND COLUMN_NAME = 'integrated_loudness_lufs'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audio_analysis_result ADD COLUMN loudness_range_lu DECIMAL(8,2) NULL COMMENT ''Loudness range in LU'' AFTER integrated_loudness_lufs',
        'SELECT ''loudness_range_lu already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_analysis_result'
      AND COLUMN_NAME = 'loudness_range_lu'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audio_analysis_result ADD COLUMN sample_peak_dbfs DECIMAL(8,2) NULL COMMENT ''Sample peak in dBFS'' AFTER loudness_range_lu',
        'SELECT ''sample_peak_dbfs already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_analysis_result'
      AND COLUMN_NAME = 'sample_peak_dbfs'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE audio_analysis_result ADD COLUMN true_peak_dbfs DECIMAL(8,2) NULL COMMENT ''True peak in dBFS'' AFTER sample_peak_dbfs',
        'SELECT ''true_peak_dbfs already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'audio_analysis_result'
      AND COLUMN_NAME = 'true_peak_dbfs'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
