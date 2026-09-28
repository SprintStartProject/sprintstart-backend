-- Persists GitHub organization-level metadata as a new artifact type and records which
-- organizations have already been fetched.
--
-- Organization metadata is fetched once per organization on the first repository connect and is
-- ingested as a dedicated GITHUB artifact with artifact_type = 'ORG_METADATA'. The gh_organizations
-- table records which organizations have already been fetched so that existsById doubles as the
-- "already connected" guard that prevents re-fetching for every repository of the same organization.
--
-- As with the rest of the schema, Hibernate's ddl-auto already emits the table; this migration is
-- idempotent so it stays a no-op against such a schema and exists so the change is recorded in the
-- migration history. The artifact and failed-item type check constraints are widened to accept the
-- new ORG_METADATA value.

CREATE TABLE IF NOT EXISTS gh_organizations (
    login VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    PRIMARY KEY (login)
);

ALTER TABLE IF EXISTS artifact
    DROP CONSTRAINT IF EXISTS chk_artifact_type;

ALTER TABLE IF EXISTS artifact
    ADD CONSTRAINT chk_artifact_type
        CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'PULL_REQUEST', 'ORG_METADATA'));

ALTER TABLE IF EXISTS ingestion_run_failed_items
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_failed_items_artifact_type;

ALTER TABLE IF EXISTS ingestion_run_failed_items
    ADD CONSTRAINT chk_ingestion_run_failed_items_artifact_type
        CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'PULL_REQUEST', 'ORG_METADATA'));
