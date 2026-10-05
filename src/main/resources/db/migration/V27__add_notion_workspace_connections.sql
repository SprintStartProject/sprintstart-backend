-- Optional schema bootstrap for manually managed databases. Hibernate update creates these
-- tables otherwise. NotionWorkspaceMigration moves legacy rows and canonical source identities
-- transactionally at startup; do not delete notion_page_connections before that has completed.
ALTER TABLE notion_credentials ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255);
ALTER TABLE notion_credentials ADD COLUMN IF NOT EXISTS workspace_name VARCHAR(2000);
ALTER TABLE notion_credentials ADD COLUMN IF NOT EXISTS token_owner_id VARCHAR(255);

CREATE TABLE IF NOT EXISTS notion_workspace_connections (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workspace_id VARCHAR(255),
    workspace_name VARCHAR(2000) NOT NULL,
    token_owner_id VARCHAR(255),
    credential_auth_id VARCHAR(255) NOT NULL,
    credential_name VARCHAR(255) NOT NULL,
    source_enabled BOOLEAN NOT NULL,
    auto_update BOOLEAN NOT NULL,
    schedule VARCHAR(255) NOT NULL,
    schedule_spec TEXT NOT NULL,
    next_sync_at TIMESTAMP WITH TIME ZONE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_notion_workspace_project_credential UNIQUE (project_id, credential_auth_id, credential_name),
    CONSTRAINT uq_notion_workspace_project_token_owner UNIQUE (project_id, workspace_id, token_owner_id)
);

CREATE TABLE IF NOT EXISTS notion_synced_pages (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    page_title VARCHAR(2000) NOT NULL,
    page_url VARCHAR(2048) NOT NULL,
    last_edited_time TIMESTAMP WITH TIME ZONE,
    content_hash VARCHAR(64),
    last_synced_at TIMESTAMP WITH TIME ZONE,
    unlinked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_notion_synced_page UNIQUE (connection_id, page_id)
);

CREATE INDEX IF NOT EXISTS idx_notion_workspace_connection_project ON notion_workspace_connections(project_id);
CREATE INDEX IF NOT EXISTS idx_notion_workspace_connection_credential
    ON notion_workspace_connections(credential_auth_id, credential_name);
