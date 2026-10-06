-- Reference-only script for removing the retired chat tables.
--
-- Nothing executes this file. The backend has no Flyway; it is kept as a
-- reviewable reference and for manually bringing a database in line if needed.
-- The actual table drop is performed by `ChatTablesDropper` on application
-- startup. `ChatTablesDropper` must only be deployed after the chat history
-- backfill has completed on every deployed environment.
--
-- The statements are idempotent and are a no-op if the tables do not exist.

DROP TABLE IF EXISTS citations;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chats;
