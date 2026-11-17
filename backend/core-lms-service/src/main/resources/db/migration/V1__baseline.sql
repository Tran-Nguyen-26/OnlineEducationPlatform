






CREATE TABLE public.ai_conversations (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    title character varying(255),
    enrollment_id uuid NOT NULL,
    lesson_id uuid
);



CREATE TABLE public.ai_document_chunks (
    id uuid NOT NULL,
    chunk_index integer NOT NULL,
    chunk_text text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    embedding_vector text,
    token_count integer NOT NULL,
    content_id uuid NOT NULL,
    course_id uuid NOT NULL,
    lesson_id uuid NOT NULL
);



CREATE TABLE public.ai_messages (
    id uuid NOT NULL,
    cited_chunk_ids jsonb,
    completion_tokens integer,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    latency_ms integer,
    prompt_tokens integer,
    is_refused boolean NOT NULL,
    role character varying(20) NOT NULL,
    conversation_id uuid NOT NULL
);



CREATE TABLE public.assignment_grades (
    id uuid NOT NULL,
    feedback text,
    graded_at timestamp(6) with time zone NOT NULL,
    previous_score numeric(5,2),
    regrade_reason text,
    regraded_at timestamp(6) with time zone,
    score numeric(5,2) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    graded_by uuid NOT NULL,
    submission_id uuid NOT NULL
);



CREATE TABLE public.assignment_submissions (
    id uuid NOT NULL,
    is_late boolean NOT NULL,
    status character varying(40) NOT NULL,
    submission_count integer NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    text_content text,
    assignment_id uuid NOT NULL,
    enrollment_id uuid NOT NULL,
    CONSTRAINT assignment_submissions_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'PENDING_REGRADE'::character varying, 'GRADED'::character varying])::text[])))
);



CREATE TABLE public.assignments (
    id uuid NOT NULL,
    allow_files boolean NOT NULL,
    allow_late_submission boolean NOT NULL,
    allow_text boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deadline timestamp(6) with time zone,
    instructions text,
    max_score numeric(5,2) NOT NULL,
    max_submissions integer NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    course_id uuid NOT NULL,
    created_by uuid,
    lesson_id uuid
);



CREATE TABLE public.attendance_records (
    id uuid NOT NULL,
    marked_at timestamp(6) with time zone NOT NULL,
    note character varying(255),
    status character varying(20) NOT NULL,
    enrollment_id uuid NOT NULL,
    marked_by uuid NOT NULL,
    session_id uuid NOT NULL,
    CONSTRAINT attendance_records_status_check CHECK (((status)::text = ANY ((ARRAY['PRESENT'::character varying, 'LATE'::character varying, 'EXCUSED'::character varying, 'ABSENT'::character varying])::text[])))
);



CREATE TABLE public.audit_logs (
    id uuid NOT NULL,
    action character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    metadata jsonb,
    target_id uuid NOT NULL,
    target_type character varying(50) NOT NULL,
    actor_id uuid NOT NULL
);



CREATE TABLE public.auth_identities (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email_verified_at timestamp(6) with time zone,
    must_change_password boolean NOT NULL,
    password_hash character varying(255),
    provider character varying(40) NOT NULL,
    provider_user_id character varying(255),
    user_id uuid NOT NULL,
    CONSTRAINT auth_identities_provider_check CHECK (((provider)::text = ANY ((ARRAY['LOCAL'::character varying, 'GOOGLE'::character varying])::text[])))
);



CREATE TABLE public.certificates (
    id uuid NOT NULL,
    certificate_number character varying(40) NOT NULL,
    issued_at timestamp(6) with time zone NOT NULL,
    enrollment_id uuid NOT NULL
);



CREATE TABLE public.completion_waivers (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    reason character varying(500),
    target_id uuid NOT NULL,
    target_type character varying(20) NOT NULL,
    created_by uuid NOT NULL,
    enrollment_id uuid NOT NULL
);



CREATE TABLE public.content_change_logs (
    id uuid NOT NULL,
    action character varying(50) NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    version_number integer NOT NULL,
    changed_by uuid,
    content_id uuid NOT NULL
);



CREATE TABLE public.course_announcements (
    id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    title character varying(255) NOT NULL,
    author_id uuid NOT NULL,
    course_id uuid NOT NULL
);



CREATE TABLE public.course_channels (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    course_id uuid NOT NULL
);



CREATE TABLE public.course_instructors (
    added_at timestamp(6) with time zone NOT NULL,
    added_by uuid,
    course_id uuid NOT NULL,
    instructor_id uuid NOT NULL
);



CREATE TABLE public.course_invite_codes (
    id uuid NOT NULL,
    is_active boolean NOT NULL,
    code character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone,
    course_id uuid NOT NULL,
    created_by uuid
);



CREATE TABLE public.course_modules (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    is_default boolean NOT NULL,
    description text,
    "position" integer NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    course_id uuid NOT NULL
);



CREATE TABLE public.course_prerequisites (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    course_id uuid NOT NULL,
    prerequisite_course_id uuid NOT NULL
);



CREATE TABLE public.course_sessions (
    id uuid NOT NULL,
    ends_at timestamp(6) with time zone,
    location character varying(255),
    note text,
    starts_at timestamp(6) with time zone NOT NULL,
    title character varying(255) NOT NULL,
    course_id uuid NOT NULL,
    created_by uuid NOT NULL
);



CREATE TABLE public.courses (
    id uuid NOT NULL,
    capacity integer,
    created_at timestamp(6) with time zone NOT NULL,
    description text,
    end_at timestamp(6) with time zone,
    learning_mode character varying(40) NOT NULL,
    lifecycle_status character varying(40) NOT NULL,
    start_at timestamp(6) with time zone,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid NOT NULL,
    subject_id uuid NOT NULL,
    fee_amount numeric(12,2),
    passing_percent integer,
    quiz_weight_percent integer DEFAULT 50 NOT NULL,
    CONSTRAINT courses_learning_mode_check CHECK (((learning_mode)::text = ANY ((ARRAY['FREE_NAVIGATION'::character varying, 'SEQUENTIAL'::character varying])::text[]))),
    CONSTRAINT courses_lifecycle_status_check CHECK (((lifecycle_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying, 'DISABLED'::character varying])::text[])))
);



CREATE TABLE public.email_notification_events (
    id uuid NOT NULL,
    event_type character varying(100),
    recipient_email character varying(255),
    retry_count integer NOT NULL,
    scheduled_at timestamp(6) with time zone,
    sent_at timestamp(6) with time zone,
    status character varying(40) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT email_notification_events_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying])::text[])))
);



CREATE TABLE public.enrollments (
    id uuid NOT NULL,
    completed_at timestamp(6) with time zone,
    enrolled_at timestamp(6) with time zone NOT NULL,
    removed_at timestamp(6) with time zone,
    status character varying(40) NOT NULL,
    withdrawn_at timestamp(6) with time zone,
    course_id uuid NOT NULL,
    invite_code_id uuid NOT NULL,
    student_id uuid NOT NULL
);



CREATE TABLE public.grade_appeals (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    reason text NOT NULL,
    resolved_at timestamp(6) with time zone,
    response text,
    status character varying(20) NOT NULL,
    target_id uuid NOT NULL,
    target_title character varying(255) NOT NULL,
    target_type character varying(30) NOT NULL,
    enrollment_id uuid NOT NULL,
    resolved_by uuid
);



CREATE TABLE public.lesson_completion_rules (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    min_video_percentage numeric(5,2),
    minimum_time_seconds integer NOT NULL,
    require_assignment boolean NOT NULL,
    require_quiz boolean NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    lesson_id uuid NOT NULL
);



CREATE TABLE public.lesson_contents (
    id uuid NOT NULL,
    content_type character varying(40) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    duration_seconds integer,
    external_url character varying(1000),
    file_name character varying(255),
    file_size bigint,
    mime_type character varying(100),
    object_key character varying(500),
    "position" integer NOT NULL,
    text_content text,
    title character varying(255),
    updated_at timestamp(6) with time zone NOT NULL,
    upload_status character varying(40) NOT NULL,
    version_number integer NOT NULL,
    created_by uuid,
    lesson_id uuid NOT NULL,
    updated_by uuid,
    CONSTRAINT lesson_contents_content_type_check CHECK (((content_type)::text = ANY ((ARRAY['TEXT'::character varying, 'LINK'::character varying, 'VIDEO'::character varying, 'PDF'::character varying, 'SLIDE'::character varying, 'DOCUMENT'::character varying, 'FILE'::character varying])::text[]))),
    CONSTRAINT lesson_contents_upload_status_check CHECK (((upload_status)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADED'::character varying, 'AVAILABLE'::character varying, 'FAILED'::character varying])::text[])))
);



CREATE TABLE public.lesson_notes (
    id uuid NOT NULL,
    content text NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    lesson_id uuid NOT NULL,
    student_id uuid NOT NULL
);



CREATE TABLE public.lesson_progress (
    id uuid NOT NULL,
    completed_at timestamp(6) with time zone,
    last_accessed_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    status character varying(40) NOT NULL,
    time_spent_seconds integer NOT NULL,
    enrollment_id uuid NOT NULL,
    last_content_id uuid,
    lesson_id uuid NOT NULL,
    watched_percent integer DEFAULT 0 NOT NULL,
    CONSTRAINT lesson_progress_status_check CHECK (((status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying])::text[])))
);



CREATE TABLE public.lessons (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description text,
    "position" integer NOT NULL,
    is_required boolean NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    module_id uuid NOT NULL
);



CREATE TABLE public.message_attachments (
    id uuid NOT NULL,
    file_name character varying(255),
    file_size bigint,
    file_type character varying(100),
    object_key character varying(500) NOT NULL,
    message_id uuid NOT NULL
);



CREATE TABLE public.messages (
    id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    edited_at timestamp(6) with time zone,
    channel_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    lesson_id uuid
);



CREATE TABLE public.notification_preferences (
    id uuid NOT NULL,
    email_enabled boolean NOT NULL,
    event_type character varying(100) NOT NULL,
    in_app_enabled boolean NOT NULL,
    reminder_before_minutes integer,
    user_id uuid NOT NULL
);



CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    dedupe_key character varying(255) NOT NULL,
    event_type character varying(100) NOT NULL,
    message text NOT NULL,
    is_read boolean NOT NULL,
    read_at timestamp(6) with time zone,
    target_id uuid,
    target_type character varying(50),
    title character varying(255) NOT NULL,
    user_id uuid NOT NULL
);



CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    event_type character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    processed_at timestamp(6) with time zone,
    retry_count integer NOT NULL,
    status character varying(40) NOT NULL,
    CONSTRAINT outbox_events_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSED'::character varying, 'FAILED'::character varying])::text[])))
);



CREATE TABLE public.password_reset_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    token_hash character varying(255) NOT NULL,
    used boolean NOT NULL,
    user_id uuid NOT NULL
);



CREATE TABLE public.question_banks (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(255) NOT NULL,
    course_id uuid NOT NULL,
    created_by uuid
);



CREATE TABLE public.question_options (
    id uuid NOT NULL,
    is_correct boolean NOT NULL,
    option_text text NOT NULL,
    "position" integer NOT NULL,
    question_id uuid NOT NULL
);



CREATE TABLE public.questions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    keyword_answer character varying(255),
    question_text text NOT NULL,
    question_type character varying(40) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    question_bank_id uuid
);



CREATE TABLE public.quiz_answers (
    id uuid NOT NULL,
    awarded_score numeric(5,2),
    is_correct boolean,
    selected_option_data jsonb,
    text_answer text,
    attempt_id uuid NOT NULL,
    question_id uuid NOT NULL,
    feedback text
);



CREATE TABLE public.quiz_attempts (
    id uuid NOT NULL,
    attempt_number integer NOT NULL,
    idempotency_key character varying(255),
    snapshot_payload jsonb,
    started_at timestamp(6) with time zone NOT NULL,
    status character varying(40) NOT NULL,
    submitted_at timestamp(6) with time zone,
    enrollment_id uuid NOT NULL,
    quiz_id uuid NOT NULL,
    CONSTRAINT quiz_attempts_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'SUBMITTED'::character varying, 'TIMED_OUT'::character varying, 'GRADED'::character varying])::text[])))
);



CREATE TABLE public.quiz_questions (
    points numeric(5,2) NOT NULL,
    "position" integer NOT NULL,
    question_id uuid NOT NULL,
    quiz_id uuid NOT NULL
);



CREATE TABLE public.quiz_results (
    id uuid NOT NULL,
    adjustment_reason text,
    auto_score numeric(5,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    final_score numeric(5,2) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    adjusted_by uuid,
    attempt_id uuid NOT NULL
);



CREATE TABLE public.quizzes (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deadline timestamp(6) with time zone,
    description text,
    grading_policy character varying(40) NOT NULL,
    is_locked boolean NOT NULL,
    max_attempts integer NOT NULL,
    question_source character varying(50),
    randomize_answers boolean,
    randomize_questions boolean,
    show_correct_answers boolean,
    show_score boolean,
    time_limit_seconds integer,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    course_id uuid NOT NULL,
    created_by uuid,
    lesson_id uuid,
    random_question_count integer,
    CONSTRAINT quizzes_grading_policy_check CHECK (((grading_policy)::text = ANY ((ARRAY['HIGHEST'::character varying, 'LATEST'::character varying, 'AVERAGE'::character varying])::text[])))
);



CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    device_info character varying(255),
    expires_at timestamp(6) with time zone NOT NULL,
    is_revoked boolean NOT NULL,
    token_hash character varying(255) NOT NULL,
    user_id uuid NOT NULL
);



CREATE TABLE public.subjects (
    id uuid NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description text,
    name character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);



CREATE TABLE public.submission_files (
    id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    file_size bigint NOT NULL,
    file_type character varying(100),
    object_key character varying(500) NOT NULL,
    upload_status character varying(40) NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    submission_id uuid NOT NULL,
    CONSTRAINT submission_files_upload_status_check CHECK (((upload_status)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADED'::character varying, 'AVAILABLE'::character varying, 'FAILED'::character varying])::text[])))
);



CREATE TABLE public.tuition_payments (
    id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    note character varying(255),
    paid_at timestamp(6) with time zone NOT NULL,
    enrollment_id uuid NOT NULL,
    recorded_by uuid NOT NULL,
    entry_type character varying(20) DEFAULT 'PAYMENT'::character varying NOT NULL,
    CONSTRAINT tuition_payments_entry_type_check CHECK (((entry_type)::text = ANY ((ARRAY['PAYMENT'::character varying, 'DISCOUNT'::character varying, 'REFUND'::character varying])::text[])))
);



CREATE TABLE public.user_profiles (
    user_id uuid NOT NULL,
    avatar_object_key character varying(500),
    bio text,
    date_of_birth date,
    full_name character varying(255) NOT NULL,
    organization character varying(255),
    phone character varying(50),
    updated_at timestamp(6) with time zone NOT NULL
);



CREATE TABLE public.users (
    id uuid NOT NULL,
    account_status character varying(40) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    role character varying(40) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_account_status_check CHECK (((account_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['STUDENT'::character varying, 'INSTRUCTOR'::character varying, 'ADMIN'::character varying])::text[])))
);



ALTER TABLE ONLY public.ai_conversations
    ADD CONSTRAINT ai_conversations_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ai_document_chunks
    ADD CONSTRAINT ai_document_chunks_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ai_messages
    ADD CONSTRAINT ai_messages_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.assignment_grades
    ADD CONSTRAINT assignment_grades_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.assignment_submissions
    ADD CONSTRAINT assignment_submissions_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT assignments_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT attendance_records_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.auth_identities
    ADD CONSTRAINT auth_identities_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT certificates_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.completion_waivers
    ADD CONSTRAINT completion_waivers_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.content_change_logs
    ADD CONSTRAINT content_change_logs_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_announcements
    ADD CONSTRAINT course_announcements_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_channels
    ADD CONSTRAINT course_channels_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_instructors
    ADD CONSTRAINT course_instructors_pkey PRIMARY KEY (course_id, instructor_id);



ALTER TABLE ONLY public.course_invite_codes
    ADD CONSTRAINT course_invite_codes_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_modules
    ADD CONSTRAINT course_modules_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_prerequisites
    ADD CONSTRAINT course_prerequisites_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.course_sessions
    ADD CONSTRAINT course_sessions_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.courses
    ADD CONSTRAINT courses_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.email_notification_events
    ADD CONSTRAINT email_notification_events_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.enrollments
    ADD CONSTRAINT enrollments_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.grade_appeals
    ADD CONSTRAINT grade_appeals_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.lesson_completion_rules
    ADD CONSTRAINT lesson_completion_rules_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.lesson_contents
    ADD CONSTRAINT lesson_contents_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.lesson_notes
    ADD CONSTRAINT lesson_notes_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.lesson_progress
    ADD CONSTRAINT lesson_progress_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT lessons_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.message_attachments
    ADD CONSTRAINT message_attachments_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.question_banks
    ADD CONSTRAINT question_banks_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.question_options
    ADD CONSTRAINT question_options_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.questions
    ADD CONSTRAINT questions_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT quiz_answers_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.quiz_attempts
    ADD CONSTRAINT quiz_attempts_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT quiz_questions_pkey PRIMARY KEY (question_id, quiz_id);



ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT quiz_results_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT quizzes_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT subjects_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.submission_files
    ADD CONSTRAINT submission_files_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.tuition_payments
    ADD CONSTRAINT tuition_payments_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT uk21ynuw4yilrbsr1nhm5mb9lre UNIQUE (enrollment_id);



ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);



ALTER TABLE ONLY public.lesson_completion_rules
    ADD CONSTRAINT uk7702lvu3vt06yjxohojou1tow UNIQUE (lesson_id);



ALTER TABLE ONLY public.assignment_grades
    ADD CONSTRAINT uk79cm6r53ivkhceasoskxsjm8s UNIQUE (submission_id);



ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT ukajre85ybxavf1tt4omkrs5p6g UNIQUE (token_hash);



ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT ukbh00wuve18hwrqs7udm2g7eib UNIQUE (attempt_id);



ALTER TABLE ONLY public.course_invite_codes
    ADD CONSTRAINT uke7ou3ryu8689hg0tcev2m8dn2 UNIQUE (code);



ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT ukfipjgqr5997w3i89i4mfyng9f UNIQUE (certificate_number);



ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT ukjc70wrs3rvlrg99quhfw9ii5k UNIQUE (dedupe_key);



ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);



ALTER TABLE ONLY public.quiz_attempts
    ADD CONSTRAINT ukpsp66rtvmj8mkyxojrie192e6 UNIQUE (idempotency_key);



ALTER TABLE ONLY public.course_channels
    ADD CONSTRAINT ukrgdhxcnksdtqlvs0e89n7av82 UNIQUE (course_id);



ALTER TABLE ONLY public.assignment_submissions
    ADD CONSTRAINT uq_assignment_submissions_assignment_id_enrollment_id UNIQUE (assignment_id, enrollment_id);



ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT uq_attendance_session_enrollment UNIQUE (session_id, enrollment_id);



ALTER TABLE ONLY public.auth_identities
    ADD CONSTRAINT uq_auth_identities_provider_provider_user_id UNIQUE (provider, provider_user_id);



ALTER TABLE ONLY public.completion_waivers
    ADD CONSTRAINT uq_completion_waivers_enrollment_target UNIQUE (enrollment_id, target_id);



ALTER TABLE ONLY public.course_prerequisites
    ADD CONSTRAINT uq_course_prerequisites_course_id_prerequisite_course_id UNIQUE (course_id, prerequisite_course_id);



ALTER TABLE ONLY public.enrollments
    ADD CONSTRAINT uq_enrollments_course_id_student_id UNIQUE (course_id, student_id);



ALTER TABLE ONLY public.lesson_notes
    ADD CONSTRAINT uq_lesson_notes_student_lesson UNIQUE (student_id, lesson_id);



ALTER TABLE ONLY public.lesson_progress
    ADD CONSTRAINT uq_lesson_progress_enrollment_id_lesson_id UNIQUE (enrollment_id, lesson_id);



ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT uq_notification_preferences_user_id_event_type UNIQUE (user_id, event_type);



ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT uq_quiz_answers_attempt_id_question_id UNIQUE (attempt_id, question_id);



ALTER TABLE ONLY public.quiz_attempts
    ADD CONSTRAINT uq_quiz_attempts_quiz_id_enrollment_id_attempt_number UNIQUE (quiz_id, enrollment_id, attempt_number);



ALTER TABLE ONLY public.subjects
    ADD CONSTRAINT uq_subjects_name UNIQUE (name);



ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (user_id);



ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);



CREATE INDEX idx_ai_document_chunks_course_id_lesson_id ON public.ai_document_chunks USING btree (course_id, lesson_id);



CREATE INDEX idx_ai_messages_conversation_id ON public.ai_messages USING btree (conversation_id);



CREATE INDEX idx_assignment_submissions_assignment_id_submitted_at ON public.assignment_submissions USING btree (assignment_id, submitted_at);



CREATE INDEX idx_assignments_course_id ON public.assignments USING btree (course_id);



CREATE INDEX idx_assignments_lesson_id ON public.assignments USING btree (lesson_id);



CREATE INDEX idx_audit_logs_actor_id ON public.audit_logs USING btree (actor_id);



CREATE INDEX idx_audit_logs_target_type_target_id ON public.audit_logs USING btree (target_type, target_id);



CREATE INDEX idx_auth_identities_user_id ON public.auth_identities USING btree (user_id);



CREATE INDEX idx_course_announcements_course_id_created_at ON public.course_announcements USING btree (course_id, created_at);



CREATE INDEX idx_course_modules_course_id_position ON public.course_modules USING btree (course_id, "position");



CREATE INDEX idx_course_sessions_course_id_starts_at ON public.course_sessions USING btree (course_id, starts_at);



CREATE INDEX idx_courses_created_by ON public.courses USING btree (created_by);



CREATE INDEX idx_courses_lifecycle_status ON public.courses USING btree (lifecycle_status);



CREATE INDEX idx_courses_subject_id ON public.courses USING btree (subject_id);



CREATE INDEX idx_enrollments_invite_code_id ON public.enrollments USING btree (invite_code_id);



CREATE INDEX idx_enrollments_student_id_status ON public.enrollments USING btree (student_id, status);



CREATE INDEX idx_grade_appeals_course_status ON public.grade_appeals USING btree (course_id, status);



CREATE INDEX idx_lesson_contents_lesson_id_position ON public.lesson_contents USING btree (lesson_id, "position");



CREATE INDEX idx_lesson_progress_enrollment_id ON public.lesson_progress USING btree (enrollment_id);



CREATE INDEX idx_lessons_module_id_position ON public.lessons USING btree (module_id, "position");



CREATE INDEX idx_messages_channel_id_created_at ON public.messages USING btree (channel_id, created_at);



CREATE INDEX idx_notifications_user_id_is_read_created_at ON public.notifications USING btree (user_id, is_read, created_at);



CREATE INDEX idx_outbox_events_status_created_at ON public.outbox_events USING btree (status, created_at);



CREATE INDEX idx_password_reset_tokens_user_id ON public.password_reset_tokens USING btree (user_id);



CREATE INDEX idx_question_options_question_id_position ON public.question_options USING btree (question_id, "position");



CREATE INDEX idx_quiz_attempts_quiz_id_status ON public.quiz_attempts USING btree (quiz_id, status);



CREATE INDEX idx_quizzes_course_id ON public.quizzes USING btree (course_id);



CREATE INDEX idx_quizzes_lesson_id ON public.quizzes USING btree (lesson_id);



CREATE INDEX idx_refresh_tokens_user_id_is_revoked ON public.refresh_tokens USING btree (user_id, is_revoked);



CREATE INDEX idx_tuition_payments_enrollment_id ON public.tuition_payments USING btree (enrollment_id);



ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.completion_waivers
    ADD CONSTRAINT fk1pass107ganym001gc2f02jkj FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT fk1qg4klxor82vbamctkjechwe2 FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.lesson_completion_rules
    ADD CONSTRAINT fk2er400dgmm747ek0e5m4gmd9x FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.lesson_notes
    ADD CONSTRAINT fk2ho9je9c7218j2k616aoe61l3 FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.enrollments
    ADD CONSTRAINT fk2lha5vwilci2yi3vu5akusx4a FOREIGN KEY (student_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.lesson_contents
    ADD CONSTRAINT fk2reocawkagthur0w94okemcbm FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.lesson_progress
    ADD CONSTRAINT fk3rogkwjafblnqgigrsd2800mo FOREIGN KEY (last_content_id) REFERENCES public.lesson_contents(id);



ALTER TABLE ONLY public.assignment_submissions
    ADD CONSTRAINT fk4j66a2r1un1cmx7emtf9iug8p FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.course_instructors
    ADD CONSTRAINT fk4mm05mmoa7y2u3obtjdk45psb FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.courses
    ADD CONSTRAINT fk4u40nf46n1nqa5h38sn5g17ac FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk4ui4nnwntodh6wjvck53dbk9m FOREIGN KEY (sender_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_instructors
    ADD CONSTRAINT fk58rc7guqiotqo24xprq96c2nv FOREIGN KEY (instructor_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.quiz_attempts
    ADD CONSTRAINT fk5kneukxp5qwx32w02hrllvo4n FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.completion_waivers
    ADD CONSTRAINT fk5m8j2t629k9cj8s9ahkft9n2t FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.courses
    ADD CONSTRAINT fk5tckdihu5akp5nkxiacx1gfhi FOREIGN KEY (subject_id) REFERENCES public.subjects(id);



ALTER TABLE ONLY public.email_notification_events
    ADD CONSTRAINT fk68cqfwriobhchuwl6h1sdyj1e FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.lesson_notes
    ADD CONSTRAINT fk69b3n3344fw545urboyfkfkuv FOREIGN KEY (student_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT fk6k3khpmt7s5v3j7yhf9issf7k FOREIGN KEY (session_id) REFERENCES public.course_sessions(id);



ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT fk6p1m72jobsvmrrn4bpj4168mg FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.lesson_contents
    ADD CONSTRAINT fk71el4fc3p165bpi6c76ytylt2 FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.ai_conversations
    ADD CONSTRAINT fk733c82nminvhdvf5cgm1y85wm FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT fk7upv291kcio5yp5nj6oeq5fm3 FOREIGN KEY (adjusted_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.quiz_results
    ADD CONSTRAINT fk82tyxt12wp3s6ntm6t9rpd3la FOREIGN KEY (attempt_id) REFERENCES public.quiz_attempts(id);



ALTER TABLE ONLY public.course_sessions
    ADD CONSTRAINT fk8fy4fsh4vqj0n4t47caw79ayg FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.content_change_logs
    ADD CONSTRAINT fk9x98bcwf4mhywrkrighx4hsrm FOREIGN KEY (content_id) REFERENCES public.lesson_contents(id);



ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk9y21adhxn0ayjhfocscqox7bh FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.questions
    ADD CONSTRAINT fka2j9n3kb9yf3bej743fxio5c3 FOREIGN KEY (question_bank_id) REFERENCES public.question_banks(id);



ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT fkajuyma3tj3agc0xu8668vgwua FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT fkanfmgf6ksbdnv7ojb0pfve54q FOREIGN KEY (quiz_id) REFERENCES public.quizzes(id);



ALTER TABLE ONLY public.question_banks
    ADD CONSTRAINT fkaoc8pganxt0sge51oqvc5oqcp FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT fkbdv8uggpsin6pnkx0d80ryqey FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.course_announcements
    ADD CONSTRAINT fkbi53enpn8kiotdtwy747pmetr FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.ai_document_chunks
    ADD CONSTRAINT fkbjpuwgl967xuobu09p1dfuvs4 FOREIGN KEY (content_id) REFERENCES public.lesson_contents(id);



ALTER TABLE ONLY public.course_instructors
    ADD CONSTRAINT fkbv45fd5esqsb8lv7or91q2xu5 FOREIGN KEY (added_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fkbxgdehp53blatm1a9hylyjsm7 FOREIGN KEY (channel_id) REFERENCES public.course_channels(id);



ALTER TABLE ONLY public.tuition_payments
    ADD CONSTRAINT fkcf3s6ns23pyttf39qlx2e18o1 FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.lesson_contents
    ADD CONSTRAINT fkcsbqhkv1nhpx62fvsmq77vddd FOREIGN KEY (updated_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.enrollments
    ADD CONSTRAINT fkcvv3963lb7q79ohj32dtp5ejn FOREIGN KEY (invite_code_id) REFERENCES public.course_invite_codes(id);



ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT fkcx3furetyjyr8gom05c6fbf82 FOREIGN KEY (question_id) REFERENCES public.questions(id);



ALTER TABLE ONLY public.grade_appeals
    ADD CONSTRAINT fke0a2p5i9gg7dhhdmvl4ql32lw FOREIGN KEY (resolved_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_invite_codes
    ADD CONSTRAINT fkeei1ps2mi44b9xco92jg61b4q FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT fkev41c723fx659v28pjycox15o FOREIGN KEY (question_id) REFERENCES public.questions(id);



ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fkf2uqqjmo7gvd8fq7ac81fgc0m FOREIGN KEY (actor_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_announcements
    ADD CONSTRAINT fkfeae7l48xp998m0a2pn5e6akp FOREIGN KEY (author_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.quiz_attempts
    ADD CONSTRAINT fkfwipvfipnnwsoacoyv5k7fbxc FOREIGN KEY (quiz_id) REFERENCES public.quizzes(id);



ALTER TABLE ONLY public.question_banks
    ADD CONSTRAINT fkg0wsceufxbth2nd710rbckq2e FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.auth_identities
    ADD CONSTRAINT fkh7r51yumnvaq9p7ay7oimaird FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_prerequisites
    ADD CONSTRAINT fkhh4f1avebuvlv54m3j3l3pp36 FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.enrollments
    ADD CONSTRAINT fkho8mcicp4196ebpltdn9wl6co FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.assignment_grades
    ADD CONSTRAINT fkif3l9ern8jrf3g1o76jmrukgj FOREIGN KEY (submission_id) REFERENCES public.assignment_submissions(id);



ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT fkj6l7dcv3gjrabhqjqsdolg78l FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.message_attachments
    ADD CONSTRAINT fkj7twd218e2gqw9cmlhwvo1rth FOREIGN KEY (message_id) REFERENCES public.messages(id);



ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT fkjcad5nfve11khsnpwj1mv8frj FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.submission_files
    ADD CONSTRAINT fkjfqehii116tkf0pt0l07msc0x FOREIGN KEY (submission_id) REFERENCES public.assignment_submissions(id);



ALTER TABLE ONLY public.certificates
    ADD CONSTRAINT fkjy46ubyh2tf64mgos6jgpcx4u FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT fkk3ndxg5xp6v7wd4gjyusp15gq FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.lesson_progress
    ADD CONSTRAINT fkkx3nc17yyecdqwfgdydqmc24x FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.ai_document_chunks
    ADD CONSTRAINT fkl5ta1njqrvbjvaf0nf3vk6ex7 FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.course_sessions
    ADD CONSTRAINT fkl5u4b9l4iem3ma04o48akvn2o FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.assignment_submissions
    ADD CONSTRAINT fkm7i7ubgh7y2n6mvg8muw62oax FOREIGN KEY (assignment_id) REFERENCES public.assignments(id);



ALTER TABLE ONLY public.questions
    ADD CONSTRAINT fkmiy2i24hb8838iqkm2kunw286 FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.ai_conversations
    ADD CONSTRAINT fkmob7ikloy3kim7eefhk4yvld7 FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.attendance_records
    ADD CONSTRAINT fko5o782w8480v8jld3vu0ysxsw FOREIGN KEY (marked_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_prerequisites
    ADD CONSTRAINT fkolf0a7iwh8c1mv9keyis4ebe8 FOREIGN KEY (prerequisite_course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT fkotqcl7qkgnihgxa6x71is49i3 FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.quizzes
    ADD CONSTRAINT fkpxdnhxeppxx606nhyjtjyharp FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT fkqfp7ioroqawsicj32pxfnggm8 FOREIGN KEY (module_id) REFERENCES public.course_modules(id);



ALTER TABLE ONLY public.course_channels
    ADD CONSTRAINT fkqsprrc8k9ctw8q47xcmril636 FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT fkqw4bm59asqarvnqso6coafn48 FOREIGN KEY (attempt_id) REFERENCES public.quiz_attempts(id);



ALTER TABLE ONLY public.lesson_progress
    ADD CONSTRAINT fkqwr70bkn0j6gok1y4op9jns8y FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.ai_document_chunks
    ADD CONSTRAINT fkr1aj8rogut4s383mw2dnttmdb FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.assignment_grades
    ADD CONSTRAINT fkr4qk0uw6l7ah8mxdu0gld70vv FOREIGN KEY (graded_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.content_change_logs
    ADD CONSTRAINT fkrasie21j8tqrfgndj0mbdeh1h FOREIGN KEY (changed_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fkrb9n235nxle1j0998ua64nwgc FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);



ALTER TABLE ONLY public.question_options
    ADD CONSTRAINT fksb9v00wdrgc9qojtjkv7e1gkp FOREIGN KEY (question_id) REFERENCES public.questions(id);



ALTER TABLE ONLY public.grade_appeals
    ADD CONSTRAINT fksrm99x0iqdnw2wvkiu507pnng FOREIGN KEY (enrollment_id) REFERENCES public.enrollments(id);



ALTER TABLE ONLY public.course_invite_codes
    ADD CONSTRAINT fksw695itfpc5qd5tec6ufttys3 FOREIGN KEY (created_by) REFERENCES public.users(id);



ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT fkt9qjvmcl36i14utm5uptyqg84 FOREIGN KEY (user_id) REFERENCES public.users(id);



ALTER TABLE ONLY public.course_modules
    ADD CONSTRAINT fktn63pa8l33002k4xuwkcuefll FOREIGN KEY (course_id) REFERENCES public.courses(id);



ALTER TABLE ONLY public.ai_messages
    ADD CONSTRAINT fktq1w3mm8r0ioqoyvvxjs765jf FOREIGN KEY (conversation_id) REFERENCES public.ai_conversations(id);



ALTER TABLE ONLY public.tuition_payments
    ADD CONSTRAINT fkuqil5o8mkvgh3e9fng23hv1f FOREIGN KEY (recorded_by) REFERENCES public.users(id);




