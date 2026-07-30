-- Adds the single project manager assignment to projects.
--
-- A project has at most one manager, expressed as a nullable foreign key rather than through the
-- project role tables. A user may manage any number of projects, and managing a project is
-- independent of being a member of it.
--
-- Note that this schema is currently created by Hibernate's ddl-auto, which already emits the
-- column, the foreign key and the index declared on the entity. This migration is written to be
-- idempotent so it stays a no-op against such a schema, and exists so the change is also expressed
-- in the migration history.

ALTER TABLE IF EXISTS sprintstart_projects
    ADD COLUMN IF NOT EXISTS manager_user_id UUID;

-- The constraint name must match the one declared on the entity's @JoinColumn, otherwise Hibernate
-- creates a second, auto-named foreign key for the same column.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_project_manager_user') THEN
        ALTER TABLE sprintstart_projects
            ADD CONSTRAINT fk_project_manager_user
                FOREIGN KEY (manager_user_id) REFERENCES sprintstart_users (id)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_projects_manager_user
    ON sprintstart_projects (manager_user_id);
