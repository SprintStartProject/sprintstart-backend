package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginSource
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.service.GithubLoginService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A user's GitHub login is what artifact verification compares a pull request's author against, so
 * these rules are grading correctness, not profile cosmetics.
 */
class GithubLoginServiceTest {
    private val userRepository: UserRepository = mockk()
    private val service = GithubLoginService(userRepository)

    private fun user() = User(
        authId = "auth-1",
        username = "alice",
        email = null,
        firstname = "Alice",
        lastname = "A",
    )

    private fun noConflict() {
        every { userRepository.existsByGithubLoginAndIdNot(any(), any()) } returns false
    }

    @Test
    fun `stores the login lower-cased so a case difference is not a different person`() {
        val user = user()
        noConflict()

        service.apply(user, "  OctoCat  ", GithubLoginSource.SELF_DECLARED)

        assertEquals("octocat", user.githubLogin)
        assertEquals(GithubLoginSource.SELF_DECLARED, user.githubLoginSource)
    }

    @Test
    fun `records who established the login`() {
        val user = user()
        noConflict()

        service.apply(user, "octocat", GithubLoginSource.PM_CONFIRMED)

        assertEquals(GithubLoginSource.PM_CONFIRMED, user.githubLoginSource)
    }

    @Test
    fun `a blank value clears the login instead of being rejected`() {
        val user = user()
        noConflict()
        service.apply(user, "octocat", GithubLoginSource.SELF_DECLARED)

        service.apply(user, "   ", GithubLoginSource.SELF_DECLARED)

        assertNull(user.githubLogin)
        assertNull(user.githubLoginSource)
    }

    @Test
    fun `rejects a syntactically impossible GitHub username`() {
        val user = user()
        noConflict()

        listOf("-leading", "trailing-", "double--hyphen", "has space", "a".repeat(40)).forEach { invalid ->
            assertThrows<ResponseStatusException> {
                service.apply(user, invalid, GithubLoginSource.SELF_DECLARED)
            }.also { assertEquals(400, it.statusCode.value()) }
        }
    }

    /**
     * A verdict is about a *value*, and it is now shown on the profile — so a stale one would put
     * "we could not find that account" next to the login that replaced it, which is the same class
     * of wrong answer the verification exists to prevent.
     */
    @Test
    fun `correcting the login discards the verdict about the old one`() {
        val user = user()
        noConflict()
        service.apply(user, "octocat", GithubLoginSource.SELF_DECLARED)
        user.githubLoginVerification = GithubLoginVerification.NOT_FOUND
        user.githubLoginVerifiedAt = Instant.now()

        service.apply(user, "octocatt", GithubLoginSource.SELF_DECLARED)

        assertNull(user.githubLoginVerification)
        assertNull(user.githubLoginVerifiedAt)
    }

    @Test
    fun `re-saving the same login keeps the verdict already reached`() {
        val user = user()
        noConflict()
        service.apply(user, "octocat", GithubLoginSource.SELF_DECLARED)
        user.githubLoginVerification = GithubLoginVerification.VERIFIED

        // Saving the rest of the profile must not cost a verification that already succeeded --
        // re-checking is 60 unauthenticated calls an hour shared by everybody.
        service.apply(user, "OctoCat", GithubLoginSource.PM_CONFIRMED)

        assertEquals(GithubLoginVerification.VERIFIED, user.githubLoginVerification)
    }

    @Test
    fun `clearing the login discards the verdict with it`() {
        val user = user()
        noConflict()
        service.apply(user, "octocat", GithubLoginSource.SELF_DECLARED)
        user.githubLoginVerification = GithubLoginVerification.VERIFIED

        service.apply(user, "", GithubLoginSource.SELF_DECLARED)

        assertNull(user.githubLoginVerification)
    }

    @Test
    fun `rejects a login another user already claims`() {
        val user = user()
        every { userRepository.existsByGithubLoginAndIdNot("octocat", user.id) } returns true

        // Two users on one GitHub account would make PR attribution ambiguous, which is exactly
        // what the attribution check exists to prevent.
        assertThrows<ResponseStatusException> {
            service.apply(user, "octocat", GithubLoginSource.SELF_DECLARED)
        }.also { assertEquals(409, it.statusCode.value()) }
    }
}
