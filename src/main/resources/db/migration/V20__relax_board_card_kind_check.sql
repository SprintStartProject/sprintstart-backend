-- board_cards is created by Hibernate (ddl-auto: update), which writes a CHECK constraint listing
-- every BoardCardKind at creation time and never widens it afterwards. A new kind — TASK_POOL here,
-- a baseline card inserted on every board read — would then fail on any database created before
-- it, and take the whole board down with it. The enum is the catalog; the column does not need to
-- repeat it. Safe on a fresh database, where the table does not exist yet.
ALTER TABLE IF EXISTS board_cards DROP CONSTRAINT IF EXISTS board_cards_kind_check;
