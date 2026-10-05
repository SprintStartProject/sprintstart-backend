-- The team areas a buddy reply opened, so the manager's later messages in the visit still have the tools that area mounts.
-- Nullable on purpose: existing messages opened nothing that matters, and Hibernate (ddl-auto: update) can add
-- a nullable column to a populated table, which it cannot do for NOT NULL ones. Idempotent.
ALTER TABLE buddy_team_messages
    ADD COLUMN IF NOT EXISTS opened_areas VARCHAR(255);
