-- The cursor that makes Bitbucket pull-request ingestion incremental.
--
-- Added separately and idempotently so a deployment that already holds connections gains its
-- pull-request cursor without losing rows, matching the file and commit cursors added in V18.
--
-- The value is the instant the last successful fetch *started*, not the instant it finished.
-- Bitbucket filters pull requests by `updated_on >= since`, so a cursor advanced to the completion
-- time would permanently skip any pull request updated while the fetch was running. A NULL means the
-- repository's pull requests have never been read, which ingestion treats as "read everything".

ALTER TABLE bitbucket_repositories
    ADD COLUMN IF NOT EXISTS last_pr_sync TIMESTAMP WITH TIME ZONE;
