-- ============================================================
-- 音频质量诊断平台 - 第一轮建表脚本
-- 版本：v1.0
-- 包含 4 张第一阶段核心表：
--   audio_file, audio_task, audio_task_stage, audio_issue
-- 当前版本的 audio_transcript_segment 由
-- backend/sql/14_create_audio_transcription.sql 创建，避免旧字段结构
-- 抢先建表后阻断后续迁移。
-- ============================================================

USE audio_agent;

-- -----------------------------------------------------------
-- 1. 音频文件元数据表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audio_file (
    id BIGINT UNSIGNED NOT NULL COMMENT '文件ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',

    source_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '来源文件ID',
    file_role TINYINT NOT NULL DEFAULT 1 COMMENT '文件角色：1原始文件 2标准化音频 3问题片段 4修复结果 5最终导出',

    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    extension VARCHAR(20) DEFAULT NULL COMMENT '文件扩展名',
    mime_type VARCHAR(100) DEFAULT NULL COMMENT 'MIME类型',

    bucket_name VARCHAR(100) NOT NULL COMMENT 'MinIO桶名称',
    object_key VARCHAR(512) NOT NULL COMMENT 'MinIO对象路径',

    size_bytes BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位字节',
    sha256 CHAR(64) DEFAULT NULL COMMENT '文件SHA-256摘要',

    duration_ms BIGINT UNSIGNED DEFAULT NULL COMMENT '音频时长，单位毫秒',
    sample_rate INT UNSIGNED DEFAULT NULL COMMENT '采样率',
    channels TINYINT UNSIGNED DEFAULT NULL COMMENT '声道数',
    bit_rate INT UNSIGNED DEFAULT NULL COMMENT '比特率',

    file_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1上传中 2可用 3处理中 4失败 5已删除',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',

    PRIMARY KEY (id),
    UNIQUE KEY uk_bucket_object (bucket_name, object_key),
    KEY idx_user_created (user_id, created_at),
    KEY idx_source_file (source_file_id),
    KEY idx_user_hash (user_id, sha256, size_bytes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频文件元数据表';

-- -----------------------------------------------------------
-- 2. 音频分析任务表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audio_task (
    id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
    task_no VARCHAR(64) NOT NULL COMMENT '对外任务编号',

    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    source_file_id BIGINT UNSIGNED NOT NULL COMMENT '原始文件ID',
    processed_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '标准化音频文件ID',

    task_type TINYINT NOT NULL DEFAULT 1 COMMENT '任务类型：1完整分析 2仅转写 3仅质量检测',

    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2等待确认 3已完成 4失败 5取消',

    current_stage VARCHAR(32) DEFAULT NULL COMMENT '当前阶段：PREPROCESS、TRANSCRIBE、ANALYZE、REPORT、FINISHED',

    progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '任务进度0-100',

    goal_text VARCHAR(1000) DEFAULT NULL COMMENT '用户自然语言处理目标',
    process_config JSON DEFAULT NULL COMMENT '分析参数配置',
    result_summary JSON DEFAULT NULL COMMENT '任务结果摘要',

    failure_stage VARCHAR(32) DEFAULT NULL COMMENT '失败阶段',
    failure_code VARCHAR(64) DEFAULT NULL COMMENT '失败错误码',
    failure_message TEXT DEFAULT NULL COMMENT '失败原因',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '任务重试次数',

    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_user_created (user_id, created_at),
    KEY idx_file_status (source_file_id, status),
    KEY idx_status_stage (status, current_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频分析任务表';

-- -----------------------------------------------------------
-- 3. 任务阶段表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audio_task_stage (
    id BIGINT UNSIGNED NOT NULL COMMENT '阶段记录ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',

    stage_code VARCHAR(32) NOT NULL COMMENT '阶段：PREPROCESS、TRANSCRIBE、ANALYZE、REPORT',

    attempt_no INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '第几次尝试',

    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待执行 1执行中 2成功 3失败 4跳过',

    progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阶段进度',

    input_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '输入文件ID',
    output_file_id BIGINT UNSIGNED DEFAULT NULL COMMENT '输出文件ID',

    input_snapshot JSON DEFAULT NULL COMMENT '输入参数快照',
    output_snapshot JSON DEFAULT NULL COMMENT '输出结果摘要',

    error_code VARCHAR(64) DEFAULT NULL COMMENT '错误码',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',

    started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '完成时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_stage_attempt (task_id, stage_code, attempt_no),
    KEY idx_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频任务阶段表';

-- -----------------------------------------------------------
-- 4. 音频问题表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audio_issue (
    id BIGINT UNSIGNED NOT NULL COMMENT '问题ID',
    task_id BIGINT UNSIGNED NOT NULL COMMENT '分析任务ID',
    file_id BIGINT UNSIGNED NOT NULL COMMENT '对应音频文件ID',

    transcript_segment_id BIGINT UNSIGNED DEFAULT NULL COMMENT '关联转写片段ID',
    issue_no INT UNSIGNED NOT NULL COMMENT '任务内问题序号',

    start_ms BIGINT UNSIGNED NOT NULL COMMENT '开始时间，毫秒',
    end_ms BIGINT UNSIGNED NOT NULL COMMENT '结束时间，毫秒',

    issue_type VARCHAR(32) NOT NULL COMMENT '问题类型：LONG_SILENCE、LOW_VOLUME、HIGH_VOLUME、NOISE、CLIPPING',

    severity TINYINT NOT NULL COMMENT '严重程度：1低 2中 3高',
    score DECIMAL(6,5) DEFAULT NULL COMMENT '问题评分0-1',

    metric_data JSON DEFAULT NULL COMMENT '检测指标',
    description VARCHAR(500) NOT NULL COMMENT '问题说明',
    suggestion VARCHAR(500) DEFAULT NULL COMMENT '修复建议',

    repairability TINYINT NOT NULL DEFAULT 0 COMMENT '可修复性：0未知 1自动修复 2人工确认 3建议删除 4建议补录',

    issue_status TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1已选择修复 2已忽略 3已修复',

    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_task_issue_no (task_id, issue_no),
    KEY idx_task_type (task_id, issue_type),
    KEY idx_task_time (task_id, start_ms, end_ms),
    KEY idx_task_status (task_id, issue_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频问题片段表';
