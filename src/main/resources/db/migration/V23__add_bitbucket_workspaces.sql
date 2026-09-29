-- Records which Bitbucket workspaces have already had their metadata and members fetched.
--
-- Workspace metadata is fetched once per workspace on the first repository connect of that
-- workspace and is ingested as a dedicated BITBUCKET artifact with artifact_type = 'ORG_METADATA'.
-- The bitbucket_workspaces table records which workspaces have already been fetched so that
-- existsById doubles as the "already connected" guard that prevents re-fetching on every
-- repository connect and update of the same workspace.
--
-- The table is created idempotently because Hibernate's schema update may have created it already on a
-- deployment that ran with the entity present.
--
-- Comments here avoid semicolons because the migration tests split the file on them.

CREATE TABLE IF NOT EXISTS bitbucket_workspaces (
    slug VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    PRIMARY KEY (slug)
);
