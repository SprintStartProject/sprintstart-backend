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

    private fun chatsTableExists(exists: Boolean) {
        every {
            jdbcTemplate.queryForObject(ChatTablesDropper.CHATS_TABLE_EXISTS, Long::class.javaObjectType)
        } returns if (exists) 1L else 0L
    }

    private fun chatsWithoutBuddySession(count: Long) {
        every {
            jdbcTemplate.queryForObject(
                ChatTablesDropper.COUNT_CHATS_WITHOUT_BUDDY_SESSION,
                Long::class.javaObjectType,
            )
        } returns count
    }

    @Test
    fun `drops retired chat tables when every chat has a buddy session`() {
        chatsTableExists(true)
        chatsWithoutBuddySession(0)
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
    fun `drops nothing while a chat has no buddy session`() {
        chatsTableExists(true)
        chatsWithoutBuddySession(2)

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }

        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }

    @Test
    fun `still runs the idempotent drops when the chats table is already gone`() {
        chatsTableExists(false)
        every { jdbcTemplate.execute(any<String>()) } just runs

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }

        verify(exactly = 0) {
            jdbcTemplate.queryForObject(
                ChatTablesDropper.COUNT_CHATS_WITHOUT_BUDDY_SESSION,
                Long::class.javaObjectType,
            )
        }
        verify(exactly = 1) { jdbcTemplate.execute(ChatTablesDropper.DROP_CITATIONS) }
        verify(exactly = 1) { jdbcTemplate.execute(ChatTablesDropper.DROP_CHAT_MESSAGES) }
        verify(exactly = 1) { jdbcTemplate.execute(ChatTablesDropper.DROP_CHATS) }
    }

    @Test
    fun `does not throw when dropping a table fails`() {
        chatsTableExists(true)
        chatsWithoutBuddySession(0)
        every {
            jdbcTemplate.execute(any<String>())
        } throws DataAccessResourceFailureException("boom")

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }
    }

    @Test
    fun `drops nothing and does not throw when the check itself fails`() {
        chatsTableExists(true)
        every {
            jdbcTemplate.queryForObject(
                ChatTablesDropper.COUNT_CHATS_WITHOUT_BUDDY_SESSION,
                Long::class.javaObjectType,
            )
        } throws DataAccessResourceFailureException("boom")

        assertDoesNotThrow {
            dropper.run(applicationArguments)
        }

        verify(exactly = 0) { jdbcTemplate.execute(any<String>()) }
    }
}
