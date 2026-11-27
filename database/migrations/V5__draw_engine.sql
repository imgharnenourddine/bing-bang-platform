CREATE TABLE draw_cycles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_number INTEGER NOT NULL UNIQUE CHECK (cycle_number > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES administrator_accounts(id),
    closed_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX draw_cycles_single_current ON draw_cycles ((true)) WHERE closed_at IS NULL;
INSERT INTO draw_cycles (cycle_number) VALUES (1);

-- All draw and reset mutations lock this same singleton row.
CREATE TABLE draw_control (id SMALLINT PRIMARY KEY CHECK (id = 1));
INSERT INTO draw_control (id) VALUES (1);

CREATE TABLE draws (
    id UUID PRIMARY KEY,
    cycle_id BIGINT NOT NULL REFERENCES draw_cycles(id),
    participant_id UUID NOT NULL REFERENCES participants(id),
    selected_by UUID NOT NULL REFERENCES administrator_accounts(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID REFERENCES administrator_accounts(id),
    CONSTRAINT draws_cycle_participant_unique UNIQUE (cycle_id, participant_id),
    CONSTRAINT draws_status_timestamps CHECK (
        (status = 'ACTIVE' AND completed_at IS NULL AND cancelled_at IS NULL AND cancelled_by IS NULL) OR
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL AND cancelled_by IS NULL) OR
        (status = 'CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
    )
);
CREATE UNIQUE INDEX draws_single_active ON draws ((true)) WHERE status = 'ACTIVE';
CREATE INDEX draws_history_created ON draws (created_at DESC, id DESC);
CREATE INDEX draws_history_cycle_status ON draws (cycle_id, status, created_at DESC);
