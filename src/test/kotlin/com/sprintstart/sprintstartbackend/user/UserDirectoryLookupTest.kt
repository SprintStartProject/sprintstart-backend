package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.ProjectRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.repository.UserSkillAssessmentRepository
import com.sprintstart.sprintstartbackend.user.service.GithubLoginService
import com.sprintstart.sprintstartbackend.user.service.UserApiService
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * The exactness of `find_user_to_add`, against a database rather than a mock.
 *
 * The contract this proves is a boundary, not a convenience: the team-mode buddy can name somebody
 * who is not on the manager's project, and the only thing keeping that from being a staff directory
 * is that the match is exact and single. A mocked repository would assert the mapping and say
 * nothing about the predicate, which is the part that would actually leak.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class UserDirectoryLookupTest {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private lateinit var service: UserApiService

    @BeforeEach
    fun setUp() {
        service = UserApiService(
            userRepository,
            mockk<UserSkillAssessmentRepository>(relaxed = true),
            projectRepository,
            mockk<GithubLoginService>(relaxed = true),
        )
    }

    private fun save(
        authId: String,
        username: String,
        email: String?,
        githubLogin: String? = null,
        firstname: String = "Sam",
        lastname: String = "Rivera",
    ): User = userRepository.save(
        User(
            authId = authId,
            username = username,
            email = email,
            firstname = firstname,
            lastname = lastname,
        ).also { it.githubLogin = githubLogin },
    )

    @Test
    fun `finds somebody by their exact email, whatever the casing`() {
        val saved = save("auth-1", "sam", "Sam@Example.test")

        val match = service.findByExactEmailOrGithubLogin("sam@EXAMPLE.TEST")

        assertThat(match).isPresent()
        assertThat(match.get().userId).isEqualTo(saved.id)
        assertThat(match.get().displayName).isEqualTo("Sam Rivera")
    }

    @Test
    fun `finds somebody by their exact GitHub login`() {
        val saved = save("auth-1", "sam", "sam@example.test", githubLogin = "sam-rivera")

        val match = service.findByExactEmailOrGithubLogin("SAM-RIVERA")

        assertThat(match).isPresent()
        assertThat(match.get().userId).isEqualTo(saved.id)
    }

    @Test
    fun `a partial identifier finds nobody, so the lookup cannot be walked`() {
        save("auth-1", "sam", "sam@example.test", githubLogin = "sam-rivera")

        // A prefix, a suffix, a substring and a wildcard: each would match under `like`.
        assertThat(service.findByExactEmailOrGithubLogin("sam@")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("example.test")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("sam")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("%")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("sam-")).isEmpty()
    }

    @Test
    fun `blank input finds nobody rather than everybody`() {
        save("auth-1", "sam", "sam@example.test")

        assertThat(service.findByExactEmailOrGithubLogin("")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("   ")).isEmpty()
    }

    @Test
    fun `two people matching is answered as nobody rather than as one of them`() {
        // One person's email is another person's GitHub login. Rare, and exactly the case where
        // answering with either would be a guess about who was meant.
        save("auth-1", "sam", "shared@example.test")
        save("auth-2", "alex", "alex@example.test", githubLogin = "shared@example.test")

        assertThat(service.findByExactEmailOrGithubLogin("shared@example.test")).isEmpty()
    }

    @Test
    fun `somebody with no email or GitHub login is not matched by a blank column`() {
        save("auth-1", "sam", email = null, githubLogin = null)

        assertThat(service.findByExactEmailOrGithubLogin("")).isEmpty()
        assertThat(service.findByExactEmailOrGithubLogin("null")).isEmpty()
    }

    @Test
    fun `the match carries a name to show and nothing else about the person`() {
        save("auth-1", "sam.rivera", "sam@example.test", firstname = "Sam", lastname = "Rivera")

        val match = service.findByExactEmailOrGithubLogin("sam@example.test").get()

        assertThat(match.displayName).isEqualTo("Sam Rivera")
        // DirectoryMatch has exactly these two properties; a caller gets no projects, roles or skills.
        assertThat(match::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("userId", "displayName")
    }
}
