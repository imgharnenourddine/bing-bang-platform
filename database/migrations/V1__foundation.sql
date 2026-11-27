-- Foundation marker: verifies that Flyway can apply a versioned migration.
CREATE TABLE foundation_marker (
    id INTEGER PRIMARY KEY,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO foundation_marker (id) VALUES (1);
