-- M7 owns this schema. IDs referring to LMS data are validated at the service boundary,
-- not declared as foreign keys into public.*.
CREATE TABLE ai_tutor.ai_conversations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT ai_conversations_title_nonempty CHECK (length(btrim(title)) > 0)
);
CREATE INDEX ai_conversations_owner_course_recent
    ON ai_tutor.ai_conversations (user_id, course_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE ai_tutor.ai_messages (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES ai_tutor.ai_conversations(id) ON DELETE CASCADE,
    role varchar(10) NOT NULL,
    content text NOT NULL,
    answer_status varchar(10),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_messages_role_status CHECK (
        (role = 'USER' AND answer_status IS NULL)
        OR (role = 'ASSISTANT' AND answer_status IN ('ANSWERED', 'ABSTAINED'))
    ),
    CONSTRAINT ai_messages_content_nonempty CHECK (length(btrim(content)) > 0)
);
CREATE INDEX ai_messages_conversation_order
    ON ai_tutor.ai_messages (conversation_id, created_at, id);

CREATE TABLE ai_tutor.ai_chat_requests (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    conversation_id uuid NOT NULL REFERENCES ai_tutor.ai_conversations(id) ON DELETE CASCADE,
    idempotency_key uuid NOT NULL,
    payload_hash char(64) NOT NULL,
    status varchar(20) NOT NULL,
    response_message_id uuid REFERENCES ai_tutor.ai_messages(id),
    attempt_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_chat_requests_unique_key UNIQUE (user_id, conversation_id, idempotency_key),
    CONSTRAINT ai_chat_requests_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL')
    ),
    CONSTRAINT ai_chat_requests_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ai_chat_requests_response CHECK (
        (status = 'COMPLETED') = (response_message_id IS NOT NULL)
    ),
    CONSTRAINT ai_chat_requests_payload_hash CHECK (payload_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE ai_tutor.ai_source_revisions (
    id uuid PRIMARY KEY,
    content_id uuid NOT NULL,
    version_number integer NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    content_type varchar(10) NOT NULL,
    source_title varchar(255) NOT NULL,
    state varchar(20) NOT NULL,
    checksum char(64),
    embedding_space_key varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    activated_at timestamptz,
    deactivated_at timestamptz,
    CONSTRAINT ai_source_revisions_content_version_space UNIQUE (content_id, version_number, embedding_space_key),
    CONSTRAINT ai_source_revisions_scope_key UNIQUE (id, course_id, lesson_id, embedding_space_key),
    CONSTRAINT ai_source_revisions_version CHECK (version_number >= 1),
    CONSTRAINT ai_source_revisions_type CHECK (content_type IN ('TEXT', 'PDF', 'FILE')),
    CONSTRAINT ai_source_revisions_state CHECK (
        state IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'UNSUPPORTED', 'SUPERSEDED', 'INACTIVE')
    )
);
CREATE UNIQUE INDEX ai_source_revisions_one_ready_per_content
    ON ai_tutor.ai_source_revisions (content_id, embedding_space_key) WHERE state = 'READY';
CREATE INDEX ai_source_revisions_course_state
    ON ai_tutor.ai_source_revisions (course_id, state, lesson_id);

CREATE TABLE ai_tutor.ai_source_chunks (
    id uuid PRIMARY KEY,
    source_revision_id uuid NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    chunk_text text NOT NULL,
    token_count integer NOT NULL,
    page_number integer,
    heading varchar(255),
    json_path varchar(500),
    embedding_space_key varchar(255) NOT NULL,
    embedding_dimension integer NOT NULL,
    vector_id varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_source_chunks_revision_scope FOREIGN KEY
        (source_revision_id, course_id, lesson_id, embedding_space_key)
        REFERENCES ai_tutor.ai_source_revisions (id, course_id, lesson_id, embedding_space_key)
        ON DELETE CASCADE,
    CONSTRAINT ai_source_chunks_position UNIQUE (source_revision_id, chunk_index),
    CONSTRAINT ai_source_chunks_index CHECK (chunk_index >= 0),
    CONSTRAINT ai_source_chunks_text CHECK (length(btrim(chunk_text)) > 0),
    CONSTRAINT ai_source_chunks_tokens CHECK (token_count > 0),
    CONSTRAINT ai_source_chunks_page CHECK (page_number IS NULL OR page_number >= 1),
    CONSTRAINT ai_source_chunks_dimension CHECK (embedding_dimension > 0)
);
CREATE INDEX ai_source_chunks_scope
    ON ai_tutor.ai_source_chunks (course_id, lesson_id, embedding_space_key);

CREATE TABLE ai_tutor.ai_message_citations (
    id uuid PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES ai_tutor.ai_messages(id) ON DELETE CASCADE,
    chunk_id uuid NOT NULL,
    content_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    source_title varchar(255) NOT NULL,
    excerpt varchar(1000) NOT NULL,
    version_number integer NOT NULL,
    page_number integer,
    heading varchar(255),
    json_path varchar(500),
    ordinal integer NOT NULL,
    CONSTRAINT ai_message_citations_order UNIQUE (message_id, ordinal),
    CONSTRAINT ai_message_citations_version CHECK (version_number >= 1),
    CONSTRAINT ai_message_citations_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ai_message_citations_page CHECK (page_number IS NULL OR page_number >= 1)
);

CREATE TABLE ai_tutor.ai_index_jobs (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    content_id uuid NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    version_number integer NOT NULL,
    action varchar(10) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    error_code varchar(100),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_index_jobs_version CHECK (version_number >= 1),
    CONSTRAINT ai_index_jobs_action CHECK (action IN ('UPSERT', 'DEACTIVATE')),
    CONSTRAINT ai_index_jobs_status CHECK (status IN ('QUEUED', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ai_index_jobs_attempts CHECK (attempt_count >= 0)
);
CREATE INDEX ai_index_jobs_queue
    ON ai_tutor.ai_index_jobs (status, created_at, id);
