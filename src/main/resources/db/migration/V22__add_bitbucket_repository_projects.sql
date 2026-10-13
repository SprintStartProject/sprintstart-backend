-- The SprintStart projects a connected Bitbucket repository is linked to.
--
-- This link is what makes the repository's artifacts visible: ingestion only announces an artifact to
-- the AI index for the projects its repository is linked to, so a connection with no row here would be
-- collected and then never indexed.
--
-- The table is created idempotently because Hibernate's schema update may have created it already on a
-- deployment that ran with the entity present. Deleting a connection drops its links. Deleting a project
-- does not, and is instead repaired by the module API that unlinks the project from every connection.
--
-- Comments here avoid semicolons because the migration tests split the file on them.

CREATE TABLE IF NOT EXISTS bitbucket_repository_projects (
    repository_id UUID NOT NULL,
    project_id UUID NOT NULL,
    PRIMARY KEY (repository_id, project_id),
    FOREIGN KEY (repository_id) REFERENCES bitbucket_repositories(id) ON DELETE CASCADE
);

-- The executor and the module API both look connections up by project, so the lookup side of the
-- primary key is indexed on its own.
CREATE INDEX IF NOT EXISTS idx_bitbucket_repository_projects_project
    ON bitbucket_repository_projects(project_id);
