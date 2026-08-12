package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStepState
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ArrivalStepServiceTest {
    private val arrivalStepRepository: ArrivalStepRepository = mockk()
    private val arrivalStepStateRepository: ArrivalStepStateRepository = mockk()
    private val userApi: UserApi = mockk()

    private val service = ArrivalStepService(arrivalStepRepository, arrivalStepStateRepository, userApi)

    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        onProjects(projectId)
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns emptyList()
        every { arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(any()) } returns emptyList()
        every { arrivalStepStateRepository.findAllByUserId(hireId) } returns emptyList()
    }

    @Test
    fun `company-wide steps apply to every hire`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("github-account"), step("vpn"))

        val steps = service.forHire(hireId)

        assertEquals(listOf("github-account", "vpn"), steps.map { it.step.key })
        assertTrue(steps.none { it.settled })
    }

    @Test
    fun `a project-scoped step with the same key replaces the company one`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("vpn", title = "Company VPN"))
        every { arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(any()) } returns
            listOf(step("vpn", projectId = projectId, title = "Team VPN"))

        val steps = service.forHire(hireId)

        assertEquals(1, steps.size, "the key must not appear twice")
        assertEquals("Team VPN", steps.single().step.title)
    }

    @Test
    fun `a hire on no projects still gets the company steps`() {
        onProjects()
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("github-account"))

        assertEquals(listOf("github-account"), service.forHire(hireId).map { it.step.key })
        // No project ids means no reason to ask for project-scoped rows at all.
        verify(exactly = 0) { arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(any()) }
    }

    /**
     * A hire's list is a union across their projects, so an id is not enough: two projects can both
     * add "Request staging access", and without the name the hire sees it twice with nothing to
     * tell them apart. The client heads each block with this.
     */
    @Test
    fun `a project step carries the name it will be grouped under`() {
        onNamedProjects(projectId to "Apollo")
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("vpn"))
        every { arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(any()) } returns
            listOf(step("staging", projectId = projectId))

        val steps = service.forHire(hireId).associateBy { it.step.key }

        assertEquals("Apollo", steps["staging"]?.projectName)
        // Company-wide is the absence of a project, on the wire as much as in the model.
        assertNull(steps["vpn"]?.projectName)
    }

    /**
     * ⚠️ Ordered by scope, never by position across scopes.
     * Positions are assigned *within* a scope, so sorting the union by them would rank a company
     * step against a project one on numbers that were never comparable.
     */
    @Test
    fun `company steps come first, then each project in name order`() {
        val orion = UUID.randomUUID()
        onNamedProjects(orion to "Orion", projectId to "Apollo")
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("vpn", position = 9))
        every { arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(any()) } returns
            listOf(
                step("runbook", projectId = orion, position = 0),
                step("staging", projectId = projectId, position = 0),
            )

        val keys = service.forHire(hireId).map { it.step.key }

        // The company step sorts first despite the highest position of the three, and Apollo
        // precedes Orion by name rather than by whichever id came back first.
        assertEquals(listOf("vpn", "staging", "runbook"), keys)
    }

    @Test
    fun `an unsettled step is not an error, it is just unsettled`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("badge"))

        val resolved = service.forHire(hireId).single()

        assertFalse(resolved.settled)
        assertNull(resolved.settledAt)
        assertNull(resolved.rigor)
    }

    @Test
    fun `confirming a step records it as declared by the hire`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("badge"))
        val saved = slot<ArrivalStepState>()
        every { arrivalStepStateRepository.save(capture(saved)) } answers { saved.captured }

        val resolved = service.confirm(hireId, "badge")

        assertTrue(resolved.settled)
        assertEquals(Rigor.DECLARED, resolved.rigor)
        assertEquals(hireId, saved.captured.userId)
        assertEquals("badge", saved.captured.stepKey)
    }

    @Test
    fun `confirming an already settled step does not move the settlement time`() {
        val settledAt = Instant.parse("2026-08-01T09:00:00Z")
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("badge"))
        every { arrivalStepStateRepository.findAllByUserId(hireId) } returns
            listOf(state("badge", settledAt = settledAt))

        val resolved = service.confirm(hireId, "badge")

        assertEquals(settledAt, resolved.settledAt, "the day something happened does not move")
        verify(exactly = 0) { arrivalStepStateRepository.save(any()) }
    }

    @Test
    fun `a step the system checks cannot be ticked by the hire`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("github-account", settledBy = Rigor.OBSERVED, selfConfirmable = false))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.confirm(hireId, "github-account")
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    /**
     * The both-ways case, and the one worth not breaking.
     *
     * "My machine builds" is observable but never refutable, and the observation arrives days after
     * it mattered. So the hire's own word has to be accepted even though a derivation exists.
     */
    @Test
    fun `a derived step that is still the hire's to claim can be confirmed`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("environment-ready", settledBy = Rigor.OBSERVED, selfConfirmable = true))
        val saved = slot<ArrivalStepState>()
        every { arrivalStepStateRepository.save(capture(saved)) } answers { saved.captured }

        val resolved = service.confirm(hireId, "environment-ready")

        assertTrue(resolved.settled)
        // Their word is recorded as their word, whatever the step could also have been settled by.
        assertEquals(Rigor.DECLARED, saved.captured.rigor)
    }

    @Test
    fun `creating a step the system knows how to check makes it a derived step`() {
        every { arrivalStepRepository.existsByKeyAndProjectIdIsNull("github-account") } returns false
        val saved = slot<ArrivalStep>()
        every { arrivalStepRepository.save(capture(saved)) } answers { saved.captured }

        // Asked for a plain self-declared step; the key says otherwise, and the key wins. A row
        // that looks derived but is not -- or is tickable when the whole point is that it must not
        // be -- would behave unlike its twin on another install, silently.
        service.create("github-account", null, "Add your GitHub username", null, null, 0, Rigor.DECLARED)

        assertEquals(Rigor.OBSERVED, saved.captured.settledBy)
        assertFalse(saved.captured.selfConfirmable)
    }

    @Test
    fun `the derivable catalog says which are already on the list`() {
        every { arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc() } returns
            listOf(step("github-account"))

        val catalog = service.derivable().toMap()

        assertEquals(true, catalog[ArrivalDerivation.GITHUB_ACCOUNT])
        assertEquals(false, catalog[ArrivalDerivation.ENVIRONMENT_READY])
    }

    @Test
    fun `confirming a step that does not apply is a 404`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            service.confirm(hireId, "nonexistent")
        }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `a duplicate key in the same scope is rejected`() {
        every { arrivalStepRepository.existsByKeyAndProjectIdIsNull("vpn") } returns true

        val error = assertThrows(ResponseStatusException::class.java) {
            service.create("vpn", null, "VPN", null, null, 0, Rigor.DECLARED)
        }

        assertEquals(
            HttpStatus.CONFLICT,
            error.statusCode,
            "Postgres will not catch this for company steps -- NULL does not conflict with NULL -- " +
                "and the test suite builds schema from entities, so the service has to",
        )
    }

    @Test
    fun `the same key is allowed in a different scope`() {
        every { arrivalStepRepository.existsByKeyAndProjectId("vpn", projectId) } returns false
        val saved = slot<ArrivalStep>()
        every { arrivalStepRepository.save(capture(saved)) } answers { saved.captured }

        service.create("vpn", projectId, "Team VPN", null, null, 0, Rigor.DECLARED)

        assertEquals(projectId, saved.captured.projectId)
    }

    @Test
    fun `a malformed key is rejected`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            service.create("Not A Key!", null, "Whatever", null, null, 0, Rigor.DECLARED)
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `deleting a definition leaves every hire's record of having done it intact`() {
        val existing = step("badge")
        every { arrivalStepRepository.findByKeyAndProjectIdIsNull("badge") } returns existing
        every { arrivalStepRepository.delete(existing) } returns Unit

        service.delete("badge", null)

        // The ledger rule, and the reason state is keyed by the step key rather than by a foreign
        // key: removing a definition must not destroy other people's history, and re-adding the
        // same key restores it.
        verify(exactly = 0) { arrivalStepStateRepository.delete(any()) }
        verify(exactly = 0) { arrivalStepStateRepository.deleteAll(any()) }
    }

    private fun onProjects(vararg ids: UUID) = onNamedProjects(*ids.map { it to "P" }.toTypedArray())

    private fun onNamedProjects(vararg named: Pair<UUID, String>) {
        val projects = named
            .map { (id, name) -> ProjectDto(projectId = id, name = name, description = null) }
            .toSet()
        every { userApi.getUsersByIds(listOf(hireId)) } returns
            listOf(
                UserDto(
                    id = hireId,
                    username = "hire",
                    firstname = "A",
                    lastname = "Hire",
                    avatarUrl = null,
                    profileIcon = null,
                    projects = projects,
                    projectRoles = emptyList(),
                ),
            )
        every { userApi.getUserIdByAuthId(any()) } returns Optional.of(hireId)
    }

    private fun step(
        key: String,
        projectId: UUID? = null,
        title: String = key,
        settledBy: Rigor = Rigor.DECLARED,
        selfConfirmable: Boolean = true,
        position: Int = 0,
    ) = ArrivalStep(
        key = key,
        projectId = projectId,
        title = title,
        settledBy = settledBy,
        selfConfirmable = selfConfirmable,
        position = position,
    )

    private fun state(
        stepKey: String,
        settledAt: Instant = Instant.now(),
        rigor: Rigor = Rigor.DECLARED,
    ) = ArrivalStepState(userId = hireId, stepKey = stepKey, rigor = rigor, settledAt = settledAt)
}
