-- When a member first saw the PM's answer to their skip request; null while it is still new to them
-- (or the request is pending). Drives the "new answer" marker on the step. Hibernate's ddl-auto
-- already emits the column, so this is idempotent and a no-op against a database it has updated.
ALTER TABLE onboarding_skips
    ADD COLUMN IF NOT EXISTS answer_seen_at TIMESTAMP(6) WITH TIME ZONE;
