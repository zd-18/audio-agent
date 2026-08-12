DROP TABLE IF EXISTS audio_content_analysis_result;
DROP TABLE IF EXISTS audio_content_analysis_task;
DROP TABLE IF EXISTS audio_transcript_segment;

CREATE TABLE audio_transcript_segment (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    speaker_label VARCHAR(64),
    text VARCHAR(10000) NOT NULL,
    confidence DECIMAL(6,5),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE audio_content_analysis_task (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    analysis_types VARCHAR(2000) NOT NULL,
    summary_style VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE audio_content_analysis_result (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    summary_json VARCHAR(10000) NOT NULL,
    key_points_json VARCHAR(10000) NOT NULL,
    chapters_json VARCHAR(10000) NOT NULL,
    speech_issues_json VARCHAR(10000) NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_content_analysis_result_task UNIQUE (task_id)
);
