DROP TABLE IF EXISTS agent_processing_workflow;
DROP TABLE IF EXISTS agent_message_citation;
DROP TABLE IF EXISTS agent_message;
DROP TABLE IF EXISTS agent_conversation;
DROP TABLE IF EXISTS audio_transcript_segment;
DROP TABLE IF EXISTS audio_transcript;
DROP TABLE IF EXISTS audio_file;
DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS audio_processing_plan;
DROP TABLE IF EXISTS audio_processing_confirmation;
DROP TABLE IF EXISTS audio_processing_step;
DROP TABLE IF EXISTS audio_processing_step_confirmation;

CREATE TABLE audio_file (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_file_id BIGINT,
    root_audio_file_id BIGINT,
    version_no INT NOT NULL DEFAULT 0,
    version_summary VARCHAR(200),
    source_execution_id BIGINT,
    file_role INT,
    original_name VARCHAR(255),
    extension VARCHAR(32),
    mime_type VARCHAR(100),
    bucket_name VARCHAR(255),
    object_key VARCHAR(1000),
    size_bytes BIGINT,
    sha256 VARCHAR(128),
    duration_ms BIGINT,
    sample_rate INT,
    channels INT,
    bit_rate INT,
    file_status INT,
    pre_delete_status INT,
    deleted_at TIMESTAMP,
    purged_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE audio_transcript (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    audio_file_id BIGINT NOT NULL,
    transcription_task_id BIGINT NOT NULL,
    language VARCHAR(32),
    full_text VARCHAR(10000),
    duration_ms BIGINT,
    speaker_count INT,
    segment_count INT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE audio_transcript_segment (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    speaker_label VARCHAR(64),
    text VARCHAR(10000) NOT NULL,
    confidence DECIMAL(6,5),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE agent_conversation (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transcript_id BIGINT NULL,
    audio_file_id BIGINT NULL,
    title VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    last_message_id BIGINT,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE audio_analysis_task (
    id BIGINT NOT NULL PRIMARY KEY,
    audio_file_id BIGINT NOT NULL,
    source_event_id BIGINT,
    analysis_type VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    progress INT,
    retry_count INT,
    max_retry_count INT,
    next_retry_at TIMESTAMP,
    error_message VARCHAR(500),
    last_error_code VARCHAR(100),
    last_message_id BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE audio_processing_plan (
    id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT,
    audio_file_id BIGINT,
    plan_version INT,
    plan_revision INT,
    plan_status VARCHAR(20),
    summary VARCHAR(500),
    step_count INT,
    estimated_output_duration_ms BIGINT,
    plan_json CLOB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE audio_processing_step (
    id BIGINT NOT NULL PRIMARY KEY,
    plan_id BIGINT,
    step_order INT,
    operation_type VARCHAR(50),
    title VARCHAR(255),
    description VARCHAR(500),
    source_issue_id BIGINT,
    start_ms BIGINT,
    end_ms BIGINT,
    priority VARCHAR(20),
    risk_level VARCHAR(20),
    requires_confirmation TINYINT,
    parameters_json CLOB,
    reason VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE audio_processing_step_confirmation (
    id BIGINT NOT NULL PRIMARY KEY,
    confirmation_id BIGINT,
    source_step_id BIGINT,
    decision VARCHAR(20),
    user_confirmed TINYINT,
    parameter_overrides_json CLOB,
    effective_parameters_json CLOB,
    user_note VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE audio_processing_confirmation (
    id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT,
    audio_file_id BIGINT,
    plan_id BIGINT,
    source_plan_revision INT,
    confirmation_status VARCHAR(20),
    accepted_step_count INT,
    rejected_step_count INT,
    pending_step_count INT,
    confirmation_json CLOB,
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE outbox_event (
    id BIGINT NOT NULL PRIMARY KEY,
    aggregate_type VARCHAR(100),
    aggregate_id VARCHAR(64),
    event_type VARCHAR(100),
    payload CLOB,
    status VARCHAR(20),
    retry_count INT,
    next_retry_at TIMESTAMP,
    locked_at TIMESTAMP,
    lock_owner VARCHAR(100),
    last_error VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    published_at TIMESTAMP
);

CREATE TABLE agent_message (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(20000),
    status VARCHAR(20) NOT NULL,
    reply_to_message_id BIGINT,
    client_request_id VARCHAR(64),
    model_name VARCHAR(100),
    prompt_version VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_message_sequence
        UNIQUE (conversation_id, sequence_no),
    CONSTRAINT uk_agent_message_request
        UNIQUE (conversation_id, client_request_id)
);

CREATE TABLE agent_message_citation (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    transcript_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    quote VARCHAR(1000) NOT NULL,
    citation_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_citation_order
        UNIQUE (message_id, citation_order)
);

CREATE TABLE agent_processing_workflow (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_message_id BIGINT NOT NULL,
    assistant_message_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    audio_file_id BIGINT NOT NULL,
    plan_id BIGINT,
    confirmation_id BIGINT,
    execution_id BIGINT,
    result_file_id BIGINT,
    workflow_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    CONSTRAINT uk_agent_workflow_user_message UNIQUE (user_message_id),
    CONSTRAINT uk_agent_workflow_execution UNIQUE (execution_id)
);
