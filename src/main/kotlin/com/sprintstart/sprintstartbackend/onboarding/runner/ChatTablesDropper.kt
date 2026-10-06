package com.sprintstart.sprintstartbackend.onboarding.runner

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Drops the retired chat tables after the chat history has been backfilled into Buddy.
 *
 * The service does not run database migrations automatically, so this is executed on
 * application startup. The statements are idempotent and therefore harmless once the
 * tables have already been removed.
 */
@Component
class ChatTablesDropper(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
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

    companion object {
        const val DROP_CITATIONS =
            "DROP TABLE IF EXISTS citations"

        const val DROP_CHAT_MESSAGES =
            "DROP TABLE IF EXISTS chat_messages"

        const val DROP_CHATS =
            "DROP TABLE IF EXISTS chats"
    }
}
