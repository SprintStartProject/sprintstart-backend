CREATE TABLE notion_credentials (
    auth_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    token TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notion_credentials PRIMARY KEY (auth_id, name)
);

CREATE TABLE notion_page_connections (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    page_title VARCHAR(2000) NOT NULL,
    page_url VARCHAR(2048) NOT NULL,
    credential_auth_id VARCHAR(255) NOT NULL,
    credential_name VARCHAR(255) NOT NULL,
    source_enabled BOOLEAN NOT NULL,
    auto_update BOOLEAN NOT NULL,
    schedule VARCHAR(255) NOT NULL,
    schedule_spec TEXT NOT NULL,
    next_sync_at TIMESTAMP WITH TIME ZONE,
    last_edited_time TIMESTAMP WITH TIME ZONE,
    content_hash VARCHAR(64),
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notion_page_connection_project
        FOREIGN KEY (project_id) REFERENCES sprintstart_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_notion_page_connection_credential
        FOREIGN KEY (credential_auth_id, credential_name)
            REFERENCES notion_credentials(auth_id, name) ON DELETE RESTRICT,
    CONSTRAINT uq_notion_page_connection_project_page
        UNIQUE (project_id, page_id)
);

CREATE INDEX idx_notion_page_connection_project
    ON notion_page_connections(project_id);

CREATE INDEX idx_notion_page_connection_credential
    ON notion_page_connections(credential_auth_id, credential_name);

CREATE INDEX idx_notion_page_connection_next_sync
    ON notion_page_connections(next_sync_at)
    WHERE auto_update = TRUE AND source_enabled = TRUE;

ALTER TABLE ingestion_run
    DROP CONSTRAINT IF EXISTS chk_ingestion_run_source_system;

ALTER TABLE ingestion_run
    ADD CONSTRAINT chk_ingestion_run_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'NOTION', 'UPLOAD'));

ALTER TABLE artifact
    DROP CONSTRAINT IF EXISTS chk_artifact_source_system;

ALTER TABLE artifact
    ADD CONSTRAINT chk_artifact_source_system
        CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'NOTION', 'UPLOAD'));
