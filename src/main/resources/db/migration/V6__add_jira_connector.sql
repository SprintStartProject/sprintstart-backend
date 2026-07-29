CREATE TABLE IF NOT EXISTS jira_credentials (
    user_email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    auth_token TEXT NOT NULL,
    PRIMARY KEY (user_email, name)
);

CREATE TABLE IF NOT EXISTS jira_instances (
    instance_url VARCHAR(2048) PRIMARY KEY,
    display_name VARCHAR(255),
    last_update TIMESTAMP NOT NULL,
    source_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL,
    update_credential_name VARCHAR(255) NOT NULL,
    update_credential_user_email VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS jira_instance_project_ids (
    instance_url VARCHAR(2048) NOT NULL,
    project_id UUID NOT NULL,
    PRIMARY KEY (instance_url, project_id),
    FOREIGN KEY (instance_url) REFERENCES jira_instances(instance_url) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS jira_instance_jira_project_keys (
    instance_url VARCHAR(2048) NOT NULL,
    jira_project_key VARCHAR(255) NOT NULL,
    PRIMARY KEY (instance_url, jira_project_key),
    FOREIGN KEY (instance_url) REFERENCES jira_instances(instance_url) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS jira_instance_configs (
    instance_url VARCHAR(2048) PRIMARY KEY,
    auto_update BOOLEAN NOT NULL DEFAULT FALSE,
    schedule VARCHAR(255) NOT NULL,
    spec TEXT NOT NULL,
    next_sync_at TIMESTAMP,
    FOREIGN KEY (instance_url) REFERENCES jira_instances(instance_url) ON DELETE CASCADE
);
