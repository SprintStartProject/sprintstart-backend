-- Bitbucket repository connections and the revision cursors that make their ingestion incremental.
--
-- The table itself is created here as well as by Hibernate's schema update, because an existing deployment
-- already has it (Hibernate created it) while a fresh one does not. Both statements are therefore idempotent,
-- and the two columns are added separately so a deployment that already holds connections gains its cursors
-- without losing the rows.

CREATE TABLE IF NOT EXISTS bitbucket_repositories (
    id UUID PRIMARY KEY,
    workspace VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    credential_auth_id VARCHAR(255) NOT NULL,
    credential_name VARCHAR(255) NOT NULL,
    connection_state VARCHAR(255) NOT NULL,
    source_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- The revision whose files were last ingested. Empty means the repository has never been ingested in full.
ALTER TABLE bitbucket_repositories
    ADD COLUMN IF NOT EXISTS last_sha VARCHAR(255) NOT NULL DEFAULT '';

-- The revision whose commits were last ingested, tracked separately from the file cursor so that
-- completing one ingest does not make the other look already done.
ALTER TABLE bitbucket_repositories
    ADD COLUMN IF NOT EXISTS last_commits_synced_sha VARCHAR(255) NOT NULL DEFAULT '';
