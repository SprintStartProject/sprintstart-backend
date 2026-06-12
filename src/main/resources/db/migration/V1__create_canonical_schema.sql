CREATE TABLE ingestion_run (
                               id UUID PRIMARY KEY,
                               source_system VARCHAR(50) NOT NULL,
                               started_at TIMESTAMP NOT NULL,
                               finished_at TIMESTAMP,
                               ingested_count INTEGER NOT NULL DEFAULT 0,
                               updated_count INTEGER NOT NULL DEFAULT 0,
                               failed_count INTEGER NOT NULL DEFAULT 0,
                               status VARCHAR(50) NOT NULL,
                               CONSTRAINT chk_ingestion_run_source_system
                                   CHECK (source_system IN ('GITHUB', 'JIRA', 'UPLOAD')),
                               CONSTRAINT chk_ingestion_run_status
                                   CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE TABLE artifact (
                          id UUID PRIMARY KEY,
                          source_system VARCHAR(50) NOT NULL,
                          source_id VARCHAR(255) NOT NULL,
                          source_url VARCHAR(2048) NOT NULL,
                          artifact_type VARCHAR(50) NOT NULL,
                          title VARCHAR(255) NOT NULL,
                          body_text TEXT NOT NULL,
                          mime VARCHAR(255),
                          language VARCHAR(50),
                          created_at_source TIMESTAMP,
                          updated_at_source TIMESTAMP,
                          ingested_at TIMESTAMP NOT NULL,
                          ingestion_run_id UUID NOT NULL,
                          content_hash VARCHAR(64) NOT NULL,
                          version VARCHAR(255) NOT NULL,

                          CONSTRAINT fk_artifact_ingestion_run
                              FOREIGN KEY (ingestion_run_id)
                                  REFERENCES ingestion_run(id),

                          CONSTRAINT chk_artifact_source_system
                              CHECK (source_system IN ('GITHUB', 'JIRA', 'UPLOAD')),

                          CONSTRAINT chk_artifact_type
                              CHECK (artifact_type IN ('COMMIT', 'FILE', 'ISSUE', 'PULL_REQUEST'))
);

CREATE INDEX idx_artifact_source
    ON artifact(source_system, source_id);

CREATE INDEX idx_artifact_ingestion_run
    ON artifact(ingestion_run_id);

CREATE INDEX idx_artifact_content_hash
    ON artifact(content_hash);