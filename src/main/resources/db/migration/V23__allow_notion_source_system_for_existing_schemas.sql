ALTER TABLE IF EXISTS ingestion_run
    DROP CONSTRAINT IF EXISTS ingestion_run_source_system_check;

ALTER TABLE IF EXISTS ingestion_run
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_source_system;

ALTER TABLE IF EXISTS ingestion_run
    ADD CONSTRAINT chk_ingestion_run_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'NOTION', 'UPLOAD'));

ALTER TABLE IF EXISTS artifact
    DROP CONSTRAINT IF EXISTS artifact_source_system_check;

ALTER TABLE IF EXISTS artifact
    DROP CONSTRAINT IF EXISTS chk_artifact_source_system;

ALTER TABLE IF EXISTS artifact
    ADD CONSTRAINT chk_artifact_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'NOTION', 'UPLOAD'));
