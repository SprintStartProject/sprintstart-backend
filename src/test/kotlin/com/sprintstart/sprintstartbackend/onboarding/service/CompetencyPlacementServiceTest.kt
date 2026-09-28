package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.DeclaredSkill
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.SkillLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CompetencyPlacementServiceTest {
    private val competencyRepository: CompetencyRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository = mockk()
    private val now: Instant = Instant.parse("2026-03-01T10:00:00Z")

    // Defaults to a user who declared nothing when they joined; the cases about adopting those
    // declarations say what they said.
    private val userApi: UserApi = mockk {
        every { getDeclaredSkills(any()) } returns emptyList()
    }

    private val service = CompetencyPlacementService(
        competencyRepository,
        userCompetencyStateRepository,
        starterWorkTaskProposalRepository,
        userApi,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val userId = UUID.randomUUID()

    private fun competency(key: String, label: String, kind: CompetencyKind = CompetencyKind.SKILL) =
        Competency(key = key, label = label, kind = kind)

    private fun ledger(key: String, level: Int, source: CompetencySource = CompetencySource.ASSESSED) =
        UserCompetencyState(userId = userId, competencyKey = key, level = level, source = source)

    private fun livePoolNeeds(vararg keys: String) {
        every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
            listOf(
                StarterWorkTaskProposal(
                    sourceId = "github:acme/web:ISSUE:1",
                    title = "Fix the header",
                    competencyKeys = keys.toMutableList(),
                ),
            )
    }

    @Test
    fun `topics are the competencies with no evidence either way`() {
        every { competencyRepository.findAll() } returns listOf(
            competency("kotlin", "Kotlin"),
            competency("testing", "Testing"),
        )
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns listOf(ledger("kotlin", 2))
        livePoolNeeds()

        assertThat(service.topicsFor(userId).map { it.key }).containsExactly("testing")
    }

    @Test
    fun `a level-0 row is still worth asking about`() {
        every { competencyRepository.findAll() } returns listOf(competency("kotlin", "Kotlin"))
        // Placed-but-unknown is the ledger recording that nobody knows, not evidence of anything.
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns listOf(ledger("kotlin", 0))
        livePoolNeeds()

        assertThat(service.topicsFor(userId).map { it.key }).containsExactly("kotlin")
    }

    @Test
    fun `what the claimable work needs comes first, and is marked`() {
        every { competencyRepository.findAll() } returns listOf(
            competency("aardvark", "Aardvark wrangling"),
            competency("kotlin", "Kotlin"),
        )
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
        livePoolNeeds("kotlin")

        val topics = service.topicsFor(userId)

        // Alphabetically "Aardvark wrangling" leads; the live pool needing Kotlin outranks that.
        assertThat(topics.map { it.key }).containsExactly("kotlin", "aardvark")
        assertThat(topics.first().neededByAvailableWork).isTrue()
        assertThat(topics.last().neededByAvailableWork).isFalse()
    }

    @Test
    fun `a short assessment is never handed more than the cap`() {
        every { competencyRepository.findAll() } returns
            (1..30).map { competency("k$it", "Competency $it") }
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
        livePoolNeeds()

        assertThat(service.topicsFor(userId)).hasSize(CompetencyPlacementService.MAX_TOPICS)
    }

    @Test
    fun `what the hire already said about themselves is not asked again`() {
        every { competencyRepository.findAll() } returns listOf(
            competency("kotlin", "Kotlin"),
            competency("testing", "Testing"),
        )
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
        every { userApi.getDeclaredSkills(userId) } returns listOf(DeclaredSkill("Kotlin", SkillLevel.ADVANCED))
        val saved = slot<UserCompetencyState>()
        every { userCompetencyStateRepository.save(capture(saved)) } answers { firstArg() }
        livePoolNeeds()

        val topics = service.topicsFor(userId)

        assertThat(saved.captured.competencyKey).isEqualTo("kotlin")
        assertThat(saved.captured.level).isEqualTo(3)
        assertThat(saved.captured.source).isEqualTo(CompetencySource.DECLARED)
        // The ledger read happens before the write in the same call, so the topic list is computed
        // from what was there; the point of the write is that the *next* visit skips it.
        assertThat(topics.map { it.key }).contains("testing")
    }

    @Test
    fun `a declared skill matches a competency by its label as well as its key`() {
        every { competencyRepository.findAll() } returns listOf(competency("ci-cd", "Continuous delivery"))
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
        every { userApi.getDeclaredSkills(userId) } returns
            listOf(DeclaredSkill("  continuous DELIVERY ", SkillLevel.BEGINNER))
        val saved = slot<UserCompetencyState>()
        every { userCompetencyStateRepository.save(capture(saved)) } answers { firstArg() }
        livePoolNeeds()

        service.topicsFor(userId)

        assertThat(saved.captured.competencyKey).isEqualTo("ci-cd")
    }

    @Test
    fun `a declared skill this team does not track is not invented as a competency`() {
        every { competencyRepository.findAll() } returns listOf(competency("kotlin", "Kotlin"))
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
        every { userApi.getDeclaredSkills(userId) } returns listOf(DeclaredSkill("Fortran", SkillLevel.EXPERT))
        livePoolNeeds()

        service.topicsFor(userId)

        verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
    }

    @Test
    fun `adopting a declaration never overwrites what the ledger already holds`() {
        every { competencyRepository.findAll() } returns listOf(competency("kotlin", "Kotlin"))
        val proven = ledger("kotlin", 4, CompetencySource.VERIFIED)
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns listOf(proven)
        every { userApi.getDeclaredSkills(userId) } returns listOf(DeclaredSkill("Kotlin", SkillLevel.BEGINNER))
        livePoolNeeds()

        service.topicsFor(userId)

        assertThat(proven.level).isEqualTo(4)
        assertThat(proven.source).isEqualTo(CompetencySource.VERIFIED)
        verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
    }

    @Test
    fun `a placement writes an assessed entry`() {
        every { competencyRepository.findByKey("kotlin") } returns competency("kotlin", "Kotlin")
        every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns null
        val saved = slot<UserCompetencyState>()
        every { userCompetencyStateRepository.save(capture(saved)) } answers { firstArg() }

        val outcome = service.record(userId, "kotlin", ProficiencyLevel.INTERMEDIATE)

        assertThat(outcome.recorded).isTrue()
        assertThat(saved.captured.level).isEqualTo(2)
        assertThat(saved.captured.source).isEqualTo(CompetencySource.ASSESSED)
    }

    @Test
    fun `a placement never lowers a level already reached`() {
        every { competencyRepository.findByKey("kotlin") } returns competency("kotlin", "Kotlin")
        val existing = ledger("kotlin", 3)
        every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns existing

        val outcome = service.record(userId, "kotlin", ProficiencyLevel.BEGINNER)

        assertThat(outcome.recorded).isTrue()
        assertThat(existing.level).isEqualTo(3)
        assertThat(existing.updatedAt).isEqualTo(now)
    }

    @Test
    fun `a placement leaves a competency the hire's accepted work already proved alone`() {
        every { competencyRepository.findByKey("kotlin") } returns competency("kotlin", "Kotlin")
        val proven = ledger("kotlin", 2, CompetencySource.VERIFIED)
        every { userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, "kotlin") } returns proven

        // Even upward: work outranks chat in both directions, so a placement never restates it.
        val outcome = service.record(userId, "kotlin", ProficiencyLevel.EXPERT)

        assertThat(outcome.recorded).isFalse()
        assertThat(proven.level).isEqualTo(2)
        assertThat(proven.source).isEqualTo(CompetencySource.VERIFIED)
        verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
    }

    @Test
    fun `a competency this team does not have is refused, not invented`() {
        every { competencyRepository.findByKey("astrology") } returns null

        val outcome = service.record(userId, "astrology", ProficiencyLevel.EXPERT)

        assertThat(outcome.recorded).isFalse()
        verify(exactly = 0) { userCompetencyStateRepository.save(any()) }
    }
}
