package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class JiraDisplayNameServiceTest {
    private val userRepository: UserRepository = mockk()
    private val service = JiraDisplayNameService(userRepository)

    private fun user() = User(
        authId = "auth-1",
        username = "ada",
        email = "ada@example.test",
        firstname = "Ada",
        lastname = "Lovelace",
    )

    private fun claimedByAnother(taken: Boolean) {
        every { userRepository.existsByJiraDisplayNameAndIdNot(any(), any()) } returns taken
    }

    @Test
    fun `stores the name`() {
        claimedByAnother(false)
        val user = user()

        service.apply(user, "  Ada Lovelace  ")

        assertThat(user.jiraDisplayName).isEqualTo("Ada Lovelace")
    }

    /**
     * ⚠️ **Not lower-cased**, unlike a GitHub login. A GitHub login is case-insensitive at the
     * source so folding it loses nothing; this is a person's name, rendered back to them and matched
     * against what Jira renders. Folding it would both misspell somebody and stop matching.
     */
    @Test
    fun `keeps the name as typed`() {
        claimedByAnother(false)
        val user = user()

        service.apply(user, "Ada de Vries")

        assertThat(user.jiraDisplayName).isEqualTo("Ada de Vries")
    }

    /** A name pasted out of Jira's UI arrives with the odd double space. */
    @Test
    fun `collapses runs of whitespace so one person is not two`() {
        claimedByAnother(false)
        val user = user()

        service.apply(user, "Ada  Lovelace")

        assertThat(user.jiraDisplayName).isEqualTo("Ada Lovelace")
    }

    /** Clearing is how somebody withdraws a wrong name, and how they opt out of being counted. */
    @Test
    fun `a blank value clears it`() {
        val user = user().apply { jiraDisplayName = "Ada Lovelace" }

        service.apply(user, "   ")

        assertThat(user.jiraDisplayName).isNull()
    }

    /**
     * ⚠️ Sharper than the GitHub case. A wrong GitHub login silently credits a hire with nothing;
     * two users claiming one Jira display name would credit one person's issues to the other.
     */
    @Test
    fun `refuses a name another user already claims`() {
        claimedByAnother(true)

        assertThatThrownBy { service.apply(user(), "Ada Lovelace") }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT)
    }

    /** No syntax rule: anything Jira renders is a valid name, and a regex would reject real people. */
    @Test
    fun `accepts a name no username pattern would allow`() {
        claimedByAnother(false)
        val user = user()

        service.apply(user, "Ada Lovelace (Contractor)")

        assertThat(user.jiraDisplayName).isEqualTo("Ada Lovelace (Contractor)")
    }
}
