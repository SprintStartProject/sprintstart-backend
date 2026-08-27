CREATE UNIQUE INDEX IF NOT EXISTS uq_artifact_confluence_source_identity
    ON artifact(source_id)
    WHERE source_system = 'CONFLUENCE';
