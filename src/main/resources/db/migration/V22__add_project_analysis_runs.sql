-- Records project_analysis_runs: every finished project analysis from the PM area, so its health
-- score history follows the project rather than one browser. The findings and checks are JSON in a
-- single TEXT column because they are read and written whole and nothing queries inside them.
--
-- As with the rest of the schema, Hibernate's ddl-auto already emits this table; this migration is
-- idempotent so it stays a no-op against such a schema and exists so the change is recorded in the
-- migration history.

CREATE TABLE IF NOT EXISTS project_analysis_runs (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    score INTEGER,
    failed_checks INTEGER NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_project_analysis_runs_project_created
    ON project_analysis_runs (project_id, created_at);
