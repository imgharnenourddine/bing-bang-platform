CREATE SEQUENCE participant_number_seq AS INTEGER START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE TABLE participants (
    id UUID PRIMARY KEY,
    participant_number INTEGER NOT NULL DEFAULT nextval('participant_number_seq'),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT participants_number_unique UNIQUE (participant_number),
    CONSTRAINT participants_number_positive CHECK (participant_number > 0)
);
ALTER SEQUENCE participant_number_seq OWNED BY participants.participant_number;
-- A one-way digest of the pre-authentication session identifies concurrent retries.
CREATE TABLE participant_registration_sessions (
    session_digest CHAR(64) PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES participants(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
