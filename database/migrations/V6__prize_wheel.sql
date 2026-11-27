CREATE TABLE prizes (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0), description VARCHAR(500),
 category VARCHAR(16) NOT NULL CHECK (category IN ('STANDARD','PREMIUM')),
 base_weight NUMERIC(16,6) NOT NULL CHECK (base_weight > 0 AND base_weight <= 1000000000),
 initial_stock INTEGER NOT NULL CHECK (initial_stock >= 0), remaining_stock INTEGER NOT NULL CHECK (remaining_stock >= 0),
 enabled BOOLEAN NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE wheel_configuration (
 id SMALLINT PRIMARY KEY CHECK(id=1), geometric_growth_factor NUMERIC(12,6) NOT NULL CHECK(geometric_growth_factor BETWEEN 1 AND 100),
 maximum_premium_multiplier NUMERIC(12,6) NOT NULL CHECK(maximum_premium_multiplier BETWEEN 1 AND 1000),
 consecutive_standard_awards BIGINT NOT NULL DEFAULT 0 CHECK(consecutive_standard_awards >= 0), updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO wheel_configuration(id,geometric_growth_factor,maximum_premium_multiplier) VALUES (1,1.5,10);
CREATE TABLE prize_awards (
 id UUID PRIMARY KEY, draw_id UUID NOT NULL UNIQUE REFERENCES draws(id), participant_id UUID NOT NULL REFERENCES participants(id),
 prize_id UUID NOT NULL REFERENCES prizes(id), awarded_by UUID NOT NULL REFERENCES administrator_accounts(id), awarded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 prize_name_snapshot VARCHAR(120) NOT NULL, prize_category_snapshot VARCHAR(16) NOT NULL CHECK(prize_category_snapshot IN ('STANDARD','PREMIUM')),
 base_weight_snapshot NUMERIC(16,6) NOT NULL CHECK(base_weight_snapshot > 0), effective_weight_snapshot NUMERIC(28,12) NOT NULL CHECK(effective_weight_snapshot > 0),
 winning_probability_snapshot NUMERIC(38,30) NOT NULL CHECK(winning_probability_snapshot > 0 AND winning_probability_snapshot <= 1),
 progression_before BIGINT NOT NULL CHECK(progression_before >= 0), progression_after BIGINT NOT NULL CHECK(progression_after >= 0),
 participant_number_snapshot INTEGER NOT NULL CHECK(participant_number_snapshot > 0), segments_snapshot JSONB NOT NULL
);
CREATE INDEX prize_awards_history ON prize_awards(awarded_at DESC,id DESC);
CREATE INDEX prize_awards_participant ON prize_awards(participant_id,awarded_at DESC);
CREATE INDEX prize_awards_prize ON prize_awards(prize_id,awarded_at DESC);
CREATE TABLE prize_stock_adjustments (
 id UUID PRIMARY KEY, prize_id UUID NOT NULL REFERENCES prizes(id), previous_stock INTEGER NOT NULL CHECK(previous_stock >= 0),
 quantity_delta INTEGER NOT NULL CHECK(quantity_delta <> 0), resulting_stock INTEGER NOT NULL CHECK(resulting_stock >= 0),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason)) > 0), adjusted_by UUID NOT NULL REFERENCES administrator_accounts(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, CHECK(previous_stock::BIGINT + quantity_delta = resulting_stock)
);
CREATE INDEX prize_stock_adjustments_prize ON prize_stock_adjustments(prize_id,created_at DESC);
