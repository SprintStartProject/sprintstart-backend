-- Who put a step on somebody's onboarding path: GENERATED (copied from the blueprint), PM, HIRE or
-- BUDDY. Hibernate's ddl-auto already emits the column, so this is idempotent and a no-op against a
-- database it has updated. Existing rows become GENERATED; a person-authored one is still
-- recognisable by is_ai_assisted being false, which is what the step badge falls back to.
ALTER TABLE onboarding_steps
    ADD COLUMN IF NOT EXISTS origin VARCHAR(16) NOT NULL DEFAULT 'GENERATED';
