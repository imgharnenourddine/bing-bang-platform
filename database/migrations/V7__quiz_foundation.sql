CREATE TABLE quiz_sessions (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT','OPEN','QUESTION_ACTIVE','QUESTION_CLOSED','FINISHED')),
    current_question_id UUID,
    question_opened_at TIMESTAMPTZ,
    question_deadline TIMESTAMPTZ,
    opened_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT quiz_session_question_times CHECK (
       (current_question_id IS NULL AND question_opened_at IS NULL AND question_deadline IS NULL) OR
       (current_question_id IS NOT NULL AND question_opened_at IS NOT NULL AND question_deadline IS NOT NULL AND question_deadline > question_opened_at)
    ),
    CONSTRAINT quiz_session_state CHECK (
       (status = 'DRAFT' AND opened_at IS NULL AND completed_at IS NULL AND current_question_id IS NULL) OR
       (status = 'OPEN' AND opened_at IS NOT NULL AND completed_at IS NULL AND current_question_id IS NULL) OR
       (status IN ('QUESTION_ACTIVE','QUESTION_CLOSED') AND opened_at IS NOT NULL AND completed_at IS NULL AND current_question_id IS NOT NULL) OR
       (status = 'FINISHED' AND opened_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at >= opened_at)
    )
);
CREATE UNIQUE INDEX quiz_one_active_session ON quiz_sessions ((true)) WHERE status IN ('OPEN','QUESTION_ACTIVE','QUESTION_CLOSED');

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY,
    quiz_session_id UUID NOT NULL REFERENCES quiz_sessions(id) ON DELETE RESTRICT,
    question_text VARCHAR(2000) NOT NULL CHECK (length(trim(question_text)) > 0),
    position INTEGER NOT NULL CHECK (position > 0),
    duration_seconds INTEGER NOT NULL DEFAULT 30 CHECK (duration_seconds > 0),
    points INTEGER NOT NULL DEFAULT 100 CHECK (points > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT quiz_question_position_unique UNIQUE (quiz_session_id, position),
    CONSTRAINT quiz_question_id_session_unique UNIQUE (id, quiz_session_id)
);
ALTER TABLE quiz_sessions ADD CONSTRAINT quiz_current_question_belongs_to_session
    FOREIGN KEY (current_question_id, id) REFERENCES quiz_questions (id, quiz_session_id) ON DELETE RESTRICT;

CREATE TABLE quiz_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
    option_text VARCHAR(1000) NOT NULL CHECK (length(trim(option_text)) > 0),
    position INTEGER NOT NULL CHECK (position > 0),
    correct BOOLEAN NOT NULL,
    CONSTRAINT quiz_option_position_unique UNIQUE (question_id, position),
    CONSTRAINT quiz_option_id_question_unique UNIQUE (id, question_id)
);
CREATE UNIQUE INDEX quiz_one_correct_option_per_question ON quiz_options(question_id) WHERE correct;

CREATE TABLE quiz_answers (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
    participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE RESTRICT,
    selected_option_id UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    awarded_points INTEGER NOT NULL CHECK (awarded_points >= 0),
    CONSTRAINT quiz_answer_one_per_participant UNIQUE (question_id, participant_id),
    CONSTRAINT quiz_answer_option_belongs_to_question FOREIGN KEY (selected_option_id, question_id)
        REFERENCES quiz_options(id, question_id) ON DELETE RESTRICT
);
CREATE INDEX quiz_answers_participant ON quiz_answers(participant_id, question_id);
