-- Records dashboard_layouts, the one row per user that holds how they arranged their dashboard:
-- which widgets are placed, in which order, at which size. The arrangement is JSON in a single
-- TEXT column because it is read and written whole and nothing ever queries inside it; `version`
-- is the client's layout version it was written under.
--
-- As with the rest of the schema, Hibernate's ddl-auto already emits this table; this migration is
-- idempotent so it stays a no-op against such a schema and exists so the change is recorded in the
-- migration history.
--
-- The unique constraint on user_id backs the upsert (ON CONFLICT (user_id) DO UPDATE), which makes
-- two tabs saving a first arrangement resolve as last-write-wins.

CREATE TABLE IF NOT EXISTS dashboard_layouts (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS dashboard_layouts
    DROP CONSTRAINT IF EXISTS uq_dashboard_layouts_user;

ALTER TABLE IF EXISTS dashboard_layouts
    ADD CONSTRAINT uq_dashboard_layouts_user UNIQUE (user_id);
