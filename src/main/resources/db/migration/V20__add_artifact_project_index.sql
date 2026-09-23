-- Reference-only migration script for the project-to-artifact join table index.
--
-- In local development and automated test environments, database schema updates
-- are automatically applied by Hibernate (spring.jpa.hibernate.ddl-auto: update)
-- from the @Index annotation on Artifact.projectIdsInternal.
--
-- In production PostgreSQL environments with high table volume, this index should
-- be created concurrently to prevent locking:
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artifact_projects_project
--     ON artifact_projects(project_id, artifact_id);

CREATE INDEX IF NOT EXISTS idx_artifact_projects_project
    ON artifact_projects(project_id, artifact_id);
