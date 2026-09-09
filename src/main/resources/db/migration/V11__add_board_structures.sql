-- Records board_structures, the one row per board that holds how a hire has arranged it: which
-- cards are for now and which for later, what waits on what, the areas things were filed into,
-- what is folded, pinned, how wide, where a kept note came from, and which sentences are marked.
-- The arrangement is JSON in a single TEXT column because it is read and written whole and nothing
-- ever queries inside it.
--
-- As with the rest of the schema, Hibernate's ddl-auto already emits this table; this migration is
-- idempotent so it stays a no-op against such a schema and exists so the change is recorded in the
-- migration history.
--
-- The unique constraint on board_id is not decoration. The write is an upsert
-- (ON CONFLICT (board_id) DO UPDATE), which is what makes two tabs arranging the same board for the
-- first time resolve as last-write-wins instead of one of them getting a constraint violation --
-- and, without the constraint, instead of both of them inserting and leaving the board with two
-- arrangements and no way to say which one is its own.

CREATE TABLE IF NOT EXISTS board_structures (
    id UUID NOT NULL,
    board_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS board_structures
    DROP CONSTRAINT IF EXISTS uq_board_structures_board;

ALTER TABLE IF EXISTS board_structures
    ADD CONSTRAINT uq_board_structures_board UNIQUE (board_id);
