-- Reconciliation writes what the tracker says about a pooled issue back onto its row, so the pool
-- can stop offering work that was closed or picked up after it was mined.
--
-- Both columns are nullable on purpose: null means "nobody has looked yet", which is a different
-- state from "looked, and the tracker said nothing". Existing rows start unchecked.
ALTER TABLE starter_work_task_proposals
    ADD COLUMN source_has_assignee BOOLEAN NULL,
    ADD COLUMN source_checked_at   TIMESTAMP NULL;
