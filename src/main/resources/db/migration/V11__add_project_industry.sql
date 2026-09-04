-- Adds the industry and industry confidence fields to projects.
--
-- Note that this schema is currently created by Hibernate's ddl-auto, which already emits the
-- columns declared on the entity. This migration is written to be idempotent so it stays a no-op
-- against such a schema, and exists so the change is also expressed in the migration history.

ALTER TABLE IF EXISTS sprintstart_projects
    ADD COLUMN IF NOT EXISTS industry VARCHAR(255);

ALTER TABLE IF EXISTS sprintstart_projects
    ADD COLUMN IF NOT EXISTS industry_confidence VARCHAR(255);
