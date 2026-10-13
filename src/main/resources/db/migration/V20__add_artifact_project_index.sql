-- Reference-only script for the project-to-artifact join-table index.
--
-- Nothing executes this file. The backend has no Flyway; schema changes reach a
-- database through Hibernate (`spring.jpa.hibernate.ddl-auto: update`), which
-- creates the index declared on `Artifact.projectIdsInternal` on the next boot.
-- It is kept so the index stays reviewable, and so a production database can be
-- brought in line by hand.
--
-- Run it OUTSIDE a transaction — CONCURRENTLY is rejected inside one — during a
-- quiet window: it takes a SHARE UPDATE EXCLUSIVE lock and never blocks writes.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artifact_projects_project
    ON artifact_projects(project_id, artifact_id);
