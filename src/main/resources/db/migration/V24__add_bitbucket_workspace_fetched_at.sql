-- Records when a Bitbucket workspace's metadata and members were last fetched.
--
-- The marker row used to mean "fetched once, never again", so workspace members fossilized the day
-- after the first repository connect. With fetched_at the connector refreshes a workspace whose
-- last fetch is older than its staleness bound instead of trusting it forever.
--
-- Nullable so the migration applies to workspaces fetched before the column existed. A null
-- timestamp reads as stale, so those workspaces refresh once on their next update rather than
-- being trusted sight unseen.
--
-- Comments here avoid semicolons because the migration tests split the file on them.

ALTER TABLE IF EXISTS bitbucket_workspaces ADD COLUMN IF NOT EXISTS fetched_at TIMESTAMPTZ NULL;
