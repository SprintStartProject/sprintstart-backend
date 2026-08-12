package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePageCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import com.sprintstart.sprintstartbackend.onboarding.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VerificationRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for the buddy's plan-and-material tools.
 *
 * The plan is a **learning area**, not a curriculum: what this project teaches, against what the
 * hire holds. The tests that used to assert graph ordering are gone with the edges — and their
 * absence is now asserted, because handing the model an order to recite is the thing this change
 * exists to stop.
 */
class BuddyPlanToolsTest {
    private val competencyModuleRepository: CompetencyModuleRepository = mockk()
    private val competencyRepository: CompetencyRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val userGoalService: UserGoalService = mockk()
    private val verificationRepository: VerificationRepository = mockk()
    private val userApi: UserApi = mockk()
    private val tools = BuddyPlanTools(
        competencyModuleRepository,
        competencyRepository,
        userCompetencyStateRepository,
        userGoalService,
        verificationRepository,
        userApi,
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val planCall = BuddyToolCallDto(id = "c0", name = "get_learning_plan")

    private fun moduleCall(competencyKey: String) = BuddyToolCallDto(
        id = "c0",
        name = "get_module",
        arguments = buildJsonObject { put("competency_key", competencyKey) },
    )

    private fun userWith(vararg projects: ProjectDto) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projects.toSet(),
        projectRoles = emptyList(),
    )

    private fun onOneProject() {
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
    }

    private fun competency(key: String, label: String, targetLevel: Int = 2, area: String? = null) =
        Competency(
            key = key,
            label = label,
            kind = CompetencyKind.SKILL,
            targetLevel = targetLevel,
            area = area,
        )

    private fun moduleFor(key: String, title: String) = CompetencyModule(
        competencyKey = key,
        projectId = projectId,
        version = 1,
        status = ModuleStatus.ACTIVE,
        title = title,
    )

    private fun ledger(vararg entries: Pair<String, Int>) {
        every { userCompetencyStateRepository.findAllByUserId(userId) } returns
            entries.map { (key, level) ->
                UserCompetencyState(
                    userId = userId,
                    competencyKey = key,
                    level = level,
                    source = CompetencySource.ASSESSED,
                )
            }
    }

    private fun noGoal() {
        every { userGoalService.findForUser(userId, projectId) } returns null
    }

    @Test
    fun `exposes the learning-plan and module tools`() {
        assertThat(tools.toolSpecs().map { it.name }).containsExactly("get_learning_plan", "get_module")
        assertThat(tools.handles("get_learning_plan")).isTrue()
        assertThat(tools.handles("get_my_metrics")).isFalse()
    }

    @Test
    fun `splits what the project teaches into gaps and what is already met`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("kotlin", "Kotlin basics"), moduleFor("react", "React basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns
            listOf(competency("kotlin", "Kotlin"), competency("react", "React"))
        ledger("kotlin" to 2, "react" to 0)

        val result = tools.execute(planCall, userId)

        assertThat(result).contains("Already met: Kotlin.")
        assertThat(result).contains("React (no evidence yet)")
        assertThat(result).contains("React basics")
    }

    /**
     * The property the whole retirement turns on: the plan must not hand the model an order to
     * recite. Sequencing is its judgement in conversation, where a hire can question it.
     */
    @Test
    fun `states no ordering between competencies`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("kotlin", "Kotlin basics"), moduleFor("react", "React basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns
            listOf(competency("kotlin", "Kotlin"), competency("react", "React"))
        ledger()

        val result = tools.execute(planCall, userId)

        assertThat(result).doesNotContain("usually comes after")
        assertThat(result).doesNotContain("Next up")
        assertThat(result).contains("shelf, not an order")
    }

    @Test
    fun `partial progress reads as a level against its bar, never as a score`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("react", "React basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns
            listOf(competency("react", "React", targetLevel = 3))
        ledger("react" to 1)

        assertThat(tools.execute(planCall, userId)).contains("React (at level 1 of 3)")
    }

    @Test
    fun `names the goal in the task's own words`() {
        onOneProject()
        every { userGoalService.findForUser(userId, projectId) } returns
            GoalView(
                proposalId = UUID.randomUUID(),
                title = "Fix the login redirect",
                summary = null,
                sourceUrl = null,
            )
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("react", "React basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns listOf(competency("react", "React"))
        ledger()

        assertThat(tools.execute(planCall, userId)).contains("Working toward: Fix the login redirect")
    }

    /**
     * A project with no published module has no learning area, and saying so beats naming gaps with
     * nothing behind them.
     */
    @Test
    fun `no published modules is an answer, not an error`() {
        onOneProject()
        noGoal()
        every {
            competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
        } returns emptyList()
        ledger()

        val result = tools.execute(planCall, userId)

        assertThat(result).contains("no learning area")
        assertThat(result).contains("search_docs")
    }

    @Test
    fun `says so plainly when the hire is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        assertThat(tools.execute(planCall, userId)).contains("not a member of any project")
    }

    @Test
    fun `teaches a published module with its pages, citations, and check prompt — never its rubric`() {
        onOneProject()
        val module = CompetencyModule(
            competencyKey = "react",
            projectId = projectId,
            version = 2,
            status = ModuleStatus.ACTIVE,
            title = "React basics",
            summary = "Components and state, grounded in our repo.",
        )
        val lesson = ModulePage(
            module = module,
            kind = ModulePageKind.LESSON,
            title = "How our components work",
            body = "Every screen composes small components.",
            position = 0,
            provenance = ContentProvenance.AI,
        )
        lesson.citations.add(
            ModulePageCitation(
                page = lesson,
                filename = "docs/frontend.md",
                chunkId = "chunk-1",
                sourceUrl = "https://example.test/docs/frontend",
                position = 0,
            ),
        )
        module.pages.add(lesson)
        module.pages.add(
            ModulePage(
                module = module,
                kind = ModulePageKind.TASK,
                title = "Build a greeting card",
                body = "Add a component that greets by name.",
                position = 1,
                provenance = ContentProvenance.AI,
            ),
        )
        every {
            competencyModuleRepository.findByCompetencyKeyAndProjectIdAndStatus("react", projectId, ModuleStatus.ACTIVE)
        } returns module
        every { verificationRepository.findByModuleId(module.id) } returns Verification(
            moduleId = module.id,
            type = VerificationType.ARTIFACT,
            prompt = "Open a PR adding the greeting component",
            rubric = "SECRET RUBRIC",
            competencyKey = "react",
            level = "intermediate",
        )

        val result = tools.execute(moduleCall("react"), userId)

        assertThat(result).contains("Module “React basics”")
        assertThat(result).contains("[LESSON] How our components work")
        assertThat(result).contains("Every screen composes small components.")
        assertThat(result).contains("docs/frontend.md (https://example.test/docs/frontend)")
        assertThat(result).contains("Check to pass (ARTIFACT): “Open a PR adding the greeting component”")
        // The rubric is what the hire is graded against — it never travels to the buddy.
        assertThat(result).doesNotContain("SECRET RUBRIC")
    }

    @Test
    fun `reports no published module so the buddy falls back to the docs`() {
        onOneProject()
        every {
            competencyModuleRepository.findByCompetencyKeyAndProjectIdAndStatus("react", projectId, ModuleStatus.ACTIVE)
        } returns null

        val result = tools.execute(moduleCall("react"), userId)

        assertThat(result).contains("No published module teaches 'react'")
        assertThat(result).contains("search_docs")
    }

    /**
     * Grouping is what replaced prerequisite structure, so the plan has to convey it -- otherwise
     * the buddy cannot answer "what else is about auth?", which is the question the area exists for.
     */
    @Test
    fun `groups gaps under what they are about`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(
                moduleFor("jwt", "JWT basics"),
                moduleFor("sessions", "Sessions"),
                moduleFor("chunking", "Chunking"),
            )
        every { competencyRepository.findAllByKeyIn(any()) } returns
            listOf(
                competency("jwt", "JWT validation", area = "Authentication"),
                competency("sessions", "Session store", area = "Authentication"),
                competency("chunking", "Chunking", area = "Ingestion"),
            )
        ledger()

        val result = tools.execute(planCall, userId)

        assertThat(result).contains("Authentication:")
        assertThat(result).contains("Ingestion:")
        // Neighbours to offer, explicitly not a sequence -- the retirement's whole point.
        assertThat(result).contains("related subjects, not steps in a sequence")
        assertThat(result).doesNotContain("usually comes after")
    }

    /**
     * Most of a hand-authored vocabulary has no `area`, and an "Ungrouped" heading over every
     * single gap is a layer that carries no information.
     */
    @Test
    fun `adds no heading when nothing is grouped yet`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("kotlin", "Kotlin basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns listOf(competency("kotlin", "Kotlin"))
        ledger()

        val result = tools.execute(planCall, userId)

        assertThat(result).contains("Kotlin (no evidence yet)")
        assertThat(result).doesNotContain("Not grouped into an area yet")
    }

    @Test
    fun `keeps ungrouped gaps visible below the grouped ones`() {
        onOneProject()
        noGoal()
        every { competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE) } returns
            listOf(moduleFor("jwt", "JWT basics"), moduleFor("kotlin", "Kotlin basics"))
        every { competencyRepository.findAllByKeyIn(any()) } returns
            listOf(
                competency("jwt", "JWT validation", area = "Authentication"),
                competency("kotlin", "Kotlin"),
            )
        ledger()

        val result = tools.execute(planCall, userId)

        assertThat(result).contains("Authentication:")
        assertThat(result).contains("Not grouped into an area yet:")
        assertThat(result).contains("Kotlin (no evidence yet)")
    }
}
