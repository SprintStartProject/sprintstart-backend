package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.EvidenceSource
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileHistory
import com.sprintstart.sprintstartbackend.offboarding.scoring.Identities
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.Touch
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SeenIdentityServiceTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val project = ProjectId(UUID.randomUUID())
    private val links = mockk<IdentityLinkService>()

    private class Fixed(
        override val id: SourceId,
        private val histories: List<FileHistory>,
    ) : EvidenceSource {
        val asked = mutableListOf<Instant>()

        override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> {
            asked += asOf
            return histories
        }
    }

    private fun touch(person: String, source: SourceId, at: String) =
        Touch(PersonKey(person), Instant.parse(at), source, founding = source == SourceId.COMMITS)

    private val commits = Fixed(
        SourceId.COMMITS,
        listOf(
            FileHistory(
                "github:acme/app",
                "a.kt",
                listOf(
                    touch("ada@x.org", SourceId.COMMITS, "2026-01-01T00:00:00Z"),
                    touch("ben", SourceId.COMMITS, "2026-02-01T00:00:00Z"),
                ),
            ),
            FileHistory(
                "github:acme/app",
                "b.kt",
                listOf(touch("ada@x.org", SourceId.COMMITS, "2026-03-01T00:00:00Z")),
            ),
        ),
    )
    private val reviews = Fixed(
        SourceId.REVIEWS,
        listOf(
            FileHistory(
                "github:acme/app",
                "a.kt",
                listOf(touch("ada", SourceId.REVIEWS, "2026-04-01T00:00:00Z")),
            ),
        ),
    )

    private fun service(vararg sources: EvidenceSource) =
        SeenIdentityService(sources.toList(), links, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `lists each identity once with its traces per source and its last trace`() {
        every { links.identities(project) } returns Identities()

        val seen = service(commits, reviews).seen(project, null)

        val ada = seen.single { it.identity == PersonKey("ada@x.org") }
        assertThat(ada.touches).containsOnly(entry(SourceId.COMMITS, 2))
        assertThat(ada.lastSeenAt).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"))
        assertThat(seen.single { it.identity == PersonKey("ada") }.touches).containsOnlyKeys(SourceId.REVIEWS)
    }

    @Test
    fun `shows who each identity counts as once links are confirmed`() {
        every { links.identities(project) } returns
            Identities(listOf(AliasLink(PersonKey("ada@x.org"), PersonKey("ada"))))

        val seen = service(commits, reviews).seen(project, null)

        assertThat(seen.single { it.identity == PersonKey("ada@x.org") }.person).isEqualTo(PersonKey("ada"))
        assertThat(seen.single { it.identity == PersonKey("ben") }.person).isEqualTo(PersonKey("ben"))
    }

    @Test
    fun `lists the most recently active identity first`() {
        every { links.identities(project) } returns Identities()

        val seen = service(commits, reviews).seen(project, null)

        assertThat(seen.map { it.identity.value }).containsExactly("ada", "ada@x.org", "ben")
    }

    @Test
    fun `an open date means the moment of the request and a given date is passed on`() {
        every { links.identities(project) } returns Identities()
        val earlier = Instant.parse("2026-02-15T00:00:00Z")

        service(commits).seen(project, null)
        service(commits).seen(project, earlier)

        assertThat(commits.asked).containsExactly(now, earlier)
    }

    @Test
    fun `a project without evidence has nobody`() {
        every { links.identities(project) } returns Identities()

        assertThat(service().seen(project, null)).isEmpty()
    }
}
