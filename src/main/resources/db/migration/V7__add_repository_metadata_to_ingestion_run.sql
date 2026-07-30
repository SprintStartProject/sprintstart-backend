-- Persists the concrete source instance a run belongs to on the ingestion run itself.
--
-- The reference is kept connector-neutral so the ingestion run stays abstract across connectors
-- (GitHub, Jira, upload, ...): source_instance_id holds the resolved instance id (for GitHub the
-- repository connection id) and source_instance_ref holds a denormalized, human-readable label
-- (for GitHub "owner/name") so the run history stays readable even if the instance is later
-- decoupled or deleted.
--
-- All columns are nullable: some sources (for example UPLOAD) have no instance, and runs created
-- before this migration have no metadata to backfill. As with the rest of the schema, Hibernate's
-- ddl-auto already emits these columns; this migration is idempotent so it stays a no-op against
-- such a schema and exists so the change is recorded in the migration history.

ALTER TABLE IF EXISTS ingestion_run
    ADD COLUMN IF NOT EXISTS source_instance_id UUID;

ALTER TABLE IF EXISTS ingestion_run
    ADD COLUMN IF NOT EXISTS source_instance_ref VARCHAR(255);
