ALTER TABLE ingestion_run
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_status;

ALTER TABLE ingestion_run
    ADD CONSTRAINT chk_ingestion_run_status
        CHECK (status IN ('CONNECTED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED'));

ALTER TABLE artifact
    ALTER COLUMN source_url DROP NOT NULL,
    ALTER COLUMN title DROP NOT NULL,
    ALTER COLUMN body_text DROP NOT NULL,
    ALTER COLUMN content_hash DROP NOT NULL;

CREATE TABLE IF NOT EXISTS ingestion_run_failed_items (
    ingestion_run_id UUID NOT NULL,
    source_id VARCHAR(255),
    artifact_type VARCHAR(50) NOT NULL,
    source_url VARCHAR(2048),
    reason TEXT NOT NULL,

    CONSTRAINT fk_ingestion_run_failed_items_run
        FOREIGN KEY (ingestion_run_id)
            REFERENCES ingestion_run(id),

    CONSTRAINT chk_ingestion_run_failed_items_artifact_type
        CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'PULL_REQUEST'))
);

CREATE INDEX IF NOT EXISTS idx_ingestion_run_failed_items_run
    ON ingestion_run_failed_items(ingestion_run_id);
