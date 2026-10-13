package com.sprintstart.sprintstartbackend.onboarding.initializer

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class BuddySchemaInitializer(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            jdbcTemplate.execute(DROP_USER_UNIQUE)
        } catch (e: DataAccessException) {
            logger.warn(
                "Could not drop uq_buddy_sessions_user; buddy sessions may still be limited to one per user: {}",
                e.message,
            )
        }
    }

    companion object {
        const val DROP_USER_UNIQUE =
            "ALTER TABLE IF EXISTS buddy_sessions DROP CONSTRAINT IF EXISTS uq_buddy_sessions_user"
    }
}
