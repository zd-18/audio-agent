DROP TABLE IF EXISTS agent_processing_workflow;
DROP TABLE IF EXISTS agent_message_citation;
DROP TABLE IF EXISTS agent_message;
DROP TABLE IF EXISTS agent_conversation;
DROP TABLE IF EXISTS audio_transcript_segment;
DROP TABLE IF EXISTS audio_transcript;
DROP TABLE IF EXISTS audio_file;

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
    transcript_id BIGINT NOT NULL,
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
