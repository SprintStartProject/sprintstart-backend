CREATE TABLE confluence_space_connections (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    space_id VARCHAR(255) NOT NULL,
    space_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_confluence_connection_project
        FOREIGN KEY (project_id) REFERENCES sprintstart_projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_confluence_connection_project_tenant_space
        UNIQUE (project_id, base_url, space_id)
);

CREATE INDEX idx_confluence_connection_project
    ON confluence_space_connections(project_id);

CREATE TABLE confluence_credentials (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL UNIQUE,
    user_email VARCHAR(255) NOT NULL,
    api_token TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_confluence_credential_connection
        FOREIGN KEY (connection_id) REFERENCES confluence_space_connections(id) ON DELETE CASCADE
);

CREATE TABLE confluence_connection_page_allowlist (
    connection_id UUID NOT NULL,
    sort_order INTEGER NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (connection_id, sort_order),
    CONSTRAINT uq_confluence_allowlist_page UNIQUE (connection_id, page_id),
    CONSTRAINT fk_confluence_allowlist_connection
        FOREIGN KEY (connection_id) REFERENCES confluence_space_connections(id) ON DELETE CASCADE
);

CREATE TABLE confluence_connection_page_denylist (
    connection_id UUID NOT NULL,
    sort_order INTEGER NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (connection_id, sort_order),
    CONSTRAINT uq_confluence_denylist_page UNIQUE (connection_id, page_id),
    CONSTRAINT fk_confluence_denylist_connection
        FOREIGN KEY (connection_id) REFERENCES confluence_space_connections(id) ON DELETE CASCADE
);
