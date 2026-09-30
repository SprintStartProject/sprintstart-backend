package com.sprintstart.sprintstartbackend.onboarding.seeding

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate

class BoardCardKindConstraintRelaxerTest {
    private val jdbcTemplate: JdbcTemplate = mockk(relaxed = true)
    private val relaxer = BoardCardKindConstraintRelaxer(jdbcTemplate)

    @Test
    fun `drops the kind check idempotently on startup`() {
        relaxer.run(DefaultApplicationArguments())

        // IF EXISTS twice: a fresh database without the table, and one whose constraint is already
        // gone, must both start cleanly.
        verify {
            jdbcTemplate.execute(
                "ALTER TABLE IF EXISTS board_cards DROP CONSTRAINT IF EXISTS board_cards_kind_check",
            )
        }
    }

    @Test
    fun `a database that refuses does not stop the app from starting`() {
        every { jdbcTemplate.execute(any<String>()) } throws DataAccessResourceFailureException("no")

        assertDoesNotThrow { relaxer.run(DefaultApplicationArguments()) }
    }
}
