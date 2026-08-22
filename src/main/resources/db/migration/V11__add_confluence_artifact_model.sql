-- Adds the canonical identifiers needed for Confluence page artifacts.
--
-- Structured Confluence page content is stored in the existing artifact.metadata JSON column.
-- Only source_version is added as a first-class artifact field because it is provenance used for
-- source-side incremental sync, not Confluence-only page structure.

ALTER TABLE IF EXISTS artifact
    ADD COLUMN IF NOT EXISTS source_version VARCHAR(255);

ALTER TABLE IF EXISTS ingestion_run
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_source_system;

ALTER TABLE IF EXISTS ingestion_run
    ADD CONSTRAINT chk_ingestion_run_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD'));

ALTER TABLE IF EXISTS artifact
    DROP CONSTRAINT IF EXISTS chk_artifact_source_system;

ALTER TABLE IF EXISTS artifact
    ADD CONSTRAINT chk_artifact_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD'));

ALTER TABLE IF EXISTS artifact
    DROP CONSTRAINT IF EXISTS chk_artifact_type;

ALTER TABLE IF EXISTS artifact
    ADD CONSTRAINT chk_artifact_type
        CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'ORG_METADATA', 'PAGE', 'PULL_REQUEST'));

ALTER TABLE IF EXISTS ingestion_run_failed_items
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_failed_items_artifact_type;

ALTER TABLE IF EXISTS ingestion_run_failed_items
    ADD CONSTRAINT chk_ingestion_run_failed_items_artifact_type
        CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'ORG_METADATA', 'PAGE', 'PULL_REQUEST'));
