package com.sprintstart.sprintstartbackend.onboarding.initializer

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class BuddySchemaInitializer(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        jdbcTemplate.execute(
            """
            ALTER TABLE buddy_sessions
            DROP CONSTRAINT IF EXISTS uq_buddy_sessions_user
            """.trimIndent(),
        )
    }
}
