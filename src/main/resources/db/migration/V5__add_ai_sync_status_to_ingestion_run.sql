ALTER TABLE ingestion_run
    ADD COLUMN IF NOT EXISTS ai_sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE ingestion_run
    ADD COLUMN IF NOT EXISTS ai_sync_failure_reason TEXT;
