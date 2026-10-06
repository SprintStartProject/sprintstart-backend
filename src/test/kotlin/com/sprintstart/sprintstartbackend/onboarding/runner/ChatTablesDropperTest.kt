package com.sprintstart.sprintstartbackend.onboarding.runner

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.ApplicationArguments
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate

@ExtendWith(MockKExtension::class)
class ChatTablesDropperTest {
    @MockK
    lateinit var jdbcTemplate: JdbcTemplate

    @MockK
    lateinit var applicationArguments: ApplicationArguments

    private lateinit var dropper: ChatTablesDropper

    @BeforeEach
    fun setUp() {
        dropper = ChatTablesDropper(jdbcTemplate)
    }

    @Test
    fun `drops retired chat tables`() {
        every { jdbcTemplate.execute(any<String>()) } just runs

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }

        verify(exactly = 1) {
            jdbcTemplate.execute(ChatTablesDropper.DROP_CITATIONS)
        }
        verify(exactly = 1) {
            jdbcTemplate.execute(ChatTablesDropper.DROP_CHAT_MESSAGES)
        }
        verify(exactly = 1) {
            jdbcTemplate.execute(ChatTablesDropper.DROP_CHATS)
        }
    }

    @Test
    fun `does not throw when dropping a table fails`() {
        every {
            jdbcTemplate.execute(any<String>())
        } throws DataAccessResourceFailureException("boom")

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }
    }
}
