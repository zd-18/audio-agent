-- AudioAgent: Planner / Executor / Critic workflow state for Agent requests.
-- MySQL 8 / Navicat. Source audio is never modified by this migration.

USE audio_agent;

CREATE TABLE IF NOT EXISTS agent_processing_workflow (
    id BIGINT NOT NULL COMMENT 'MyBatis-Plus snowflake workflow ID',
    user_id BIGINT NOT NULL COMMENT 'Owner snapshot',
    conversation_id BIGINT NOT NULL,
    user_message_id BIGINT NOT NULL COMMENT 'Idempotency source message',
    assistant_message_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL COMMENT 'Completed audio_analysis_task.id',
    audio_file_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT NULL,
    confirmation_id BIGINT NULL,
    execution_id BIGINT NULL,
    result_file_id BIGINT UNSIGNED NULL,
    workflow_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_workflow_user_message (user_message_id),
    UNIQUE KEY uk_agent_workflow_execution (execution_id),
    KEY idx_agent_workflow_conversation (user_id, conversation_id, created_at),
    KEY idx_agent_workflow_status (workflow_status),
    CONSTRAINT chk_agent_workflow_status CHECK (
        workflow_status IN (
            'PLANNING', 'WAITING_CONFIRMATION', 'EXECUTING',
            'REVIEWING', 'SUCCESS', 'FAILED'
        )
    ),
    CONSTRAINT fk_agent_workflow_conversation FOREIGN KEY (conversation_id)
        REFERENCES agent_conversation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_user_message FOREIGN KEY (user_message_id)
        REFERENCES agent_message(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_assistant_message FOREIGN KEY (assistant_message_id)
        REFERENCES agent_message(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_task FOREIGN KEY (task_id)
        REFERENCES audio_analysis_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_audio FOREIGN KEY (audio_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_plan FOREIGN KEY (plan_id)
        REFERENCES audio_processing_plan(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_confirmation FOREIGN KEY (confirmation_id)
        REFERENCES audio_processing_confirmation(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_execution FOREIGN KEY (execution_id)
        REFERENCES audio_processing_execution(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_workflow_result FOREIGN KEY (result_file_id)
        REFERENCES audio_file(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Planner / Executor / Critic state for Agent processing requests';
