ALTER TABLE confluence_space_connections
    ADD COLUMN source_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE confluence_space_connections
    ALTER COLUMN source_enabled DROP DEFAULT;
