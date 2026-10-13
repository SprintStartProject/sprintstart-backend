-- The update configuration of every connected Bitbucket repository.
--
-- A config shares its primary key with the repository it belongs to, so a connection has at most one
-- config and the two cannot drift apart. The table is created idempotently because Hibernate's schema
-- update may have created it already on a deployment that ran with the entity present.

CREATE TABLE IF NOT EXISTS bb_repository_configs (
    repository_id UUID PRIMARY KEY,
    auto_update BOOLEAN NOT NULL DEFAULT TRUE,
    schedule VARCHAR(255) NOT NULL DEFAULT '0 0 2 * * *',
    spec TEXT,
    next_sync_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (repository_id) REFERENCES bitbucket_repositories(id) ON DELETE CASCADE
);

-- Backfill: a connection that predates the config table would otherwise never be scheduled. It gets
-- the default daily schedule, matching the auto-update-on default of a newly connected repository.
-- The NOT EXISTS guard keeps the insert idempotent without a dialect-specific conflict clause.
INSERT INTO bb_repository_configs (repository_id, auto_update, schedule, spec, next_sync_at)
SELECT r.id, TRUE, '0 0 2 * * *', '{"type":"DAILY","time":[2,0]}', CURRENT_TIMESTAMP
FROM bitbucket_repositories r
WHERE NOT EXISTS (SELECT 1 FROM bb_repository_configs c WHERE c.repository_id = r.id);

-- The executor's due lookup filters on next_sync_at, so it is indexed.
CREATE INDEX IF NOT EXISTS idx_bitbucket_configs_next_sync
    ON bb_repository_configs(next_sync_at)
    WHERE auto_update = TRUE;
