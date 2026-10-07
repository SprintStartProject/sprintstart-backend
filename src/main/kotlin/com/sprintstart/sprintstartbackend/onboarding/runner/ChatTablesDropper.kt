package com.sprintstart.sprintstartbackend.onboarding.runner

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Drops the retired chat tables once every chat has been carried over into Buddy.
 *
 * The service does not run database migrations automatically, so this is executed on
 * application startup. The drop statements are idempotent and therefore harmless once the
 * tables have already been removed.
 *
 * The backfill keeps each chat's id as the id of its Buddy session, and the frontend's
 * `/chat/:id` redirect relies on that. Before dropping anything, the runner therefore counts the
 * chats that have no Buddy session with the same id. If there is any, the history has not been
 * fully backfilled, so nothing is dropped and an error is logged; the next start checks again.
 * If `chats` no longer exists there is nothing to verify and the drops run as before.
 */
@Component
class ChatTablesDropper(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            if (chatsTableExists()) {
                val withoutSession = countChatsWithoutBuddySession()
                if (withoutSession > 0) {
                    logger.error(
                        "Not dropping the retired chat tables: {} chat(s) have no Buddy session with the same id. " +
                            "Run the chat history backfill first.",
                        withoutSession,
                    )
                    return
                }
            }
            jdbcTemplate.execute(DROP_CITATIONS)
            jdbcTemplate.execute(DROP_CHAT_MESSAGES)
            jdbcTemplate.execute(DROP_CHATS)
        } catch (e: DataAccessException) {
            logger.warn(
                "Could not drop retired chat tables: {}",
                e.message,
            )
        }
    }

    private fun chatsTableExists(): Boolean =
        (jdbcTemplate.queryForObject(CHATS_TABLE_EXISTS, Long::class.javaObjectType) ?: 0L) > 0L

    private fun countChatsWithoutBuddySession(): Long =
        jdbcTemplate.queryForObject(COUNT_CHATS_WITHOUT_BUDDY_SESSION, Long::class.javaObjectType) ?: 0L

    companion object {
        const val CHATS_TABLE_EXISTS =
            "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = current_schema() AND table_name = 'chats'"

        const val COUNT_CHATS_WITHOUT_BUDDY_SESSION =
            "SELECT count(*) FROM chats c WHERE NOT EXISTS (SELECT 1 FROM buddy_sessions s WHERE s.id = c.id)"

        const val DROP_CITATIONS =
            "DROP TABLE IF EXISTS citations"

        const val DROP_CHAT_MESSAGES =
            "DROP TABLE IF EXISTS chat_messages"

        const val DROP_CHATS =
            "DROP TABLE IF EXISTS chats"
    }
}
