ALTER TABLE quiz_sessions ADD COLUMN question_closed_at TIMESTAMPTZ;
ALTER TABLE quiz_sessions ADD CONSTRAINT quiz_closed_time_valid CHECK (
    (status = 'QUESTION_CLOSED' AND question_closed_at IS NOT NULL AND question_closed_at >= question_opened_at AND question_closed_at <= question_deadline)
 OR (status = 'FINISHED' AND (current_question_id IS NULL OR (question_closed_at IS NOT NULL AND question_closed_at >= question_opened_at AND question_closed_at <= question_deadline)))
 OR (status IN ('DRAFT','OPEN','QUESTION_ACTIVE') AND question_closed_at IS NULL)
) NOT VALID;
CREATE TABLE quiz_question_timings (
    question_id UUID PRIMARY KEY,
    quiz_session_id UUID NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ NOT NULL CHECK (deadline > opened_at),
    closed_at TIMESTAMPTZ,
    CONSTRAINT quiz_timing_question_fk FOREIGN KEY (question_id, quiz_session_id) REFERENCES quiz_questions (id, quiz_session_id) ON DELETE RESTRICT,
    CONSTRAINT quiz_timing_close_valid CHECK (closed_at IS NULL OR (closed_at >= opened_at AND closed_at <= deadline))
);
CREATE INDEX quiz_expired_active ON quiz_sessions(question_deadline) WHERE status = 'QUESTION_ACTIVE';
