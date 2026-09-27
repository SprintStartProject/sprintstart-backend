-- The revision whose commits the GitHub connector last ingested.
--
-- Commits used to be selected by the snapshot's `last_commits_sync_at` timestamp, which silently
-- drops a commit authored before the boundary but pushed after it. This cursor makes the selection
-- exact: the range is `last_commits_synced_sha..HEAD`.
--
-- The column is added separately from the table so an existing deployment keeps its connections
-- and gains the cursor without losing the rows.

ALTER TABLE gh_repository_connections
    ADD COLUMN IF NOT EXISTS last_commits_synced_sha VARCHAR(255) NOT NULL DEFAULT '';
