package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.SeenIdentity
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.DirectoryMatch
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class MemberIdentityServiceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val seen = mockk<SeenIdentityService>()
    private val membership = mockk<ProjectMembershipApi>()
    private val users = mockk<UserApi>()
    private val now = Instant.parse("2026-03-10T00:00:00Z")
    private val service = MemberIdentityService(seen, membership, users, Clock.fixed(now, ZoneOffset.UTC))

    private val ada = UUID.randomUUID()
    private val ben = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val at = Instant.parse("2026-03-01T00:00:00Z")

    private fun member(id: UUID, name: String, login: String? = null) =
        ProjectMember(userId = id, displayName = name, githubLogin = login, joinedAt = null)

    private fun trace(
        identity: String,
        commits: Int = 0,
        reviews: Int = 0,
        person: String = identity,
        lastSeenAt: Instant = at,
    ) =
        SeenIdentity(
            identity = PersonKey(identity),
            person = PersonKey(person),
            touches = mapOf(SourceId.COMMITS to commits, SourceId.REVIEWS to reviews).filterValues { it > 0 },
            lastSeenAt = lastSeenAt,
        )

    private fun owners(vararg owners: Pair<String, UUID>) {
        val byIdentity = owners.toMap()
        every { users.findByExactEmailOrGithubLogin(any()) } answers {
            val owner = byIdentity[firstArg<String>()]
            if (owner == null) Optional.empty() else Optional.of(DirectoryMatch(owner, "someone"))
        }
    }

    private fun evidence(vararg identities: SeenIdentity) {
        every { seen.seen(project, null) } returns identities.toList()
    }

    private fun team(vararg members: ProjectMember) {
        every { membership.getProjectMembers(project.value) } returns members.toList()
    }

    @Test
    fun `an identity is matched to the member whose email or login it is`() {
        team(member(ada, "Ada", "ada"))
        evidence(trace("ada@x.org", commits = 5), trace("ben@y.org", commits = 2))
        owners("ada@x.org" to ada)

        val match = service.matches(project, null).single()

        assertThat(match.member).isEqualTo(UserId(ada))
        assertThat(match.identities.map { it.identity.value }).containsExactly("ada@x.org")
        assertThat(match.matched).isTrue()
        assertThat(match.suggestions).isEmpty()
    }

    @Test
    fun `a member nothing in the evidence belongs to is reported as not matched`() {
        team(member(ada, "Ada"), member(ben, "Ben"))
        evidence(trace("ada@x.org", commits = 5))
        owners("ada@x.org" to ada)

        val matches = service.matches(project, null).associateBy { it.displayName }

        assertThat(matches.getValue("Ada").matched).isTrue()
        assertThat(matches.getValue("Ben").matched).isFalse()
        assertThat(matches.getValue("Ben").identities).isEmpty()
    }

    @Test
    fun `an identity of somebody who is not a member of the project is left out`() {
        team(member(ada, "Ada"))
        evidence(trace("ada@x.org", commits = 5), trace("out@z.org", commits = 9))
        owners("ada@x.org" to ada, "out@z.org" to outsider)

        val match = service.matches(project, null).single()

        assertThat(match.identities.map { it.identity.value }).containsExactly("ada@x.org")
    }

    @Test
    fun `identities nobody registered stay unmatched`() {
        team(member(ada, "Ada"))
        evidence(trace("stranger@q.org", commits = 3))
        owners()

        assertThat(service.matches(project, null).single().matched).isFalse()
    }

    @Test
    fun `a member with an email and a login in the evidence gets a link suggested toward the login`() {
        team(member(ada, "Ada", "ada"))
        evidence(trace("ada@x.org", commits = 5), trace("ada", reviews = 2))
        owners("ada@x.org" to ada, "ada" to ada)

        val match = service.matches(project, null).single()

        assertThat(match.suggestions).containsExactly(AliasLink(PersonKey("ada@x.org"), PersonKey("ada")))
    }

    @Test
    fun `without a login the identity with most traces is kept and the rest folded into it`() {
        team(member(ada, "Ada"))
        evidence(
            trace("ada@x.org", commits = 5),
            trace("ada@home.org", commits = 1),
            trace("ada@work.org", commits = 3),
        )
        owners("ada@x.org" to ada, "ada@home.org" to ada, "ada@work.org" to ada)

        val match = service.matches(project, null).single()

        assertThat(match.suggestions).containsExactly(
            AliasLink(PersonKey("ada@home.org"), PersonKey("ada@x.org")),
            AliasLink(PersonKey("ada@work.org"), PersonKey("ada@x.org")),
        )
    }

    @Test
    fun `identities that already count as one person need no suggestion`() {
        team(member(ada, "Ada", "ada"))
        evidence(trace("ada@x.org", commits = 5, person = "ada"), trace("ada", reviews = 2))
        owners("ada@x.org" to ada, "ada" to ada)

        assertThat(service.matches(project, null).single().suggestions).isEmpty()
    }

    @Test
    fun `members are listed by name whatever the case`() {
        team(member(ben, "ben"), member(ada, "Ada"))
        evidence()
        owners()

        assertThat(service.matches(project, null).map { it.displayName }).containsExactly("Ada", "ben")
    }

    @Test
    fun `a given date is passed on to the evidence`() {
        val earlier = Instant.parse("2026-01-01T00:00:00Z")
        team(member(ada, "Ada"))
        every { seen.seen(project, earlier) } returns emptyList()
        owners()

        assertThat(service.matches(project, earlier).single().matched).isFalse()
    }

    @Test
    fun `a member who left no trace for more than a year is inactive`() {
        team(member(ada, "Ada"))
        evidence(trace("ada@x.org", commits = 5, lastSeenAt = now.minus(Duration.ofDays(400))))
        owners("ada@x.org" to ada)

        val match = service.matches(project, null).single()

        assertThat(match.inactive).isTrue()
        assertThat(match.lastSeenAt).isEqualTo(now.minus(Duration.ofDays(400)))
    }

    @Test
    fun `a member with a recent trace under any identity is active`() {
        team(member(ada, "Ada"))
        evidence(
            trace("ada@x.org", commits = 5, lastSeenAt = now.minus(Duration.ofDays(400))),
            trace("ada@old.org", commits = 1, lastSeenAt = now.minus(Duration.ofDays(20))),
        )
        owners("ada@x.org" to ada, "ada@old.org" to ada)

        val match = service.matches(project, null).single()

        assertThat(match.inactive).isFalse()
        assertThat(match.lastSeenAt).isEqualTo(now.minus(Duration.ofDays(20)))
    }

    @Test
    fun `a member nothing is known of is not inactive and has no last trace`() {
        team(member(ben, "Ben"))
        evidence()

        val match = service.matches(project, null).single()

        assertThat(match.inactive).isFalse()
        assertThat(match.lastSeenAt).isNull()
    }

    @Test
    fun `a past date is the point the inactivity is counted from`() {
        val past = Instant.parse("2026-01-01T00:00:00Z")
        team(member(ada, "Ada"))
        every { seen.seen(project, past) } returns
            listOf(trace("ada@x.org", commits = 1, lastSeenAt = past.minus(Duration.ofDays(30))))
        owners("ada@x.org" to ada)

        assertThat(service.matches(project, past).single().inactive).isFalse()
    }
}
