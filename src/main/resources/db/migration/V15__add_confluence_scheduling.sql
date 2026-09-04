ALTER TABLE confluence_space_connections
    ADD COLUMN auto_update BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE confluence_space_connections
    ADD COLUMN schedule VARCHAR(255) NOT NULL DEFAULT '0 0 2 * * *';

ALTER TABLE confluence_space_connections
    ADD COLUMN spec TEXT;

ALTER TABLE confluence_space_connections
    ADD COLUMN next_sync_at TIMESTAMP WITH TIME ZONE;

UPDATE confluence_space_connections
SET spec = '{"type":"DAILY","time":[2,0]}'
WHERE spec IS NULL;

ALTER TABLE confluence_space_connections
    ALTER COLUMN spec SET NOT NULL;

ALTER TABLE confluence_space_connections
    ALTER COLUMN auto_update DROP DEFAULT;

ALTER TABLE confluence_space_connections
    ALTER COLUMN schedule DROP DEFAULT;

CREATE INDEX idx_confluence_connection_next_sync
    ON confluence_space_connections(next_sync_at)
    WHERE auto_update = TRUE AND source_enabled = TRUE;
