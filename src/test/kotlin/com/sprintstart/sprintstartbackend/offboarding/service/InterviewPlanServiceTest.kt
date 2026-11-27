package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.offboarding.external.InterviewAiClient
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewQuestion
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewRequest
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.Concentration
import com.sprintstart.sprintstartbackend.offboarding.scoring.CoupledFile
import com.sprintstart.sprintstartbackend.offboarding.scoring.Coupling
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskSummary
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.Successor
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InterviewPlanServiceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val request = RiskRequest(setOf(PersonKey("ada")), asOf, WeightProfile())
    private val risks = mockk<OffboardingRiskService>()
    private val ai = mockk<InterviewAiClient>()
    private val service = InterviewPlanService(risks, ai)

    private fun risk(path: String) = FileRisk(
        repository = "github:acme/app",
        path = path,
        level = RiskLevel.HOARDED,
        score = 0.8,
        departingShare = 1.0,
        sourceBreakdown = mapOf(SourceId.COMMITS to 1.0),
        recentActivity = 0.4,
        fragmentation = 0.0,
        concentration = Concentration(1.0, PersonKey("ada")),
        remainingKnowers = listOf(PersonKey("ben")),
        lastChangedAt = asOf,
        coupling = Coupling(0.5, listOf(CoupledFile("github:acme/app", "b.kt", 0.5))),
        hotspot = 0.7,
        kind = FileKind.CODE,
        abandonedShare = 0.6,
        successors = listOf(Successor(PersonKey("cy"), 0.4)),
        reasons = listOf("Only one person who knows it is left."),
    )

    private fun mapOf(vararg files: FileRisk) =
        RiskMap(
            asOf,
            WeightProfile(),
            setOf(PersonKey("ada")),
            emptySet(),
            RiskSummary(files.size, 0, 0, 0),
            files.toList(),
        )

    private val question =
        AiInterviewQuestion("github:acme/app/src", "Why?", "rationale", listOf("hotspot"), listOf("a.kt"), 0.6, "open")

    @Test
    fun `the plan is made from the files at risk with their findings and no names`() = runTest {
        every { risks.riskMap(project, request) } returns mapOf(risk("src/a.kt"))
        val sent = slot<AiInterviewRequest>()
        coEvery { ai.planInterview(capture(sent)) } returns AiInterviewResponse(listOf(question), listOf(question))

        val plan = service.plan(project, request, 10)

        val file = sent.captured.files.single()
        assertThat(sent.captured.projectId).isEqualTo(project.value.toString())
        assertThat(file.path).isEqualTo("src/a.kt")
        assertThat(file.level).isEqualTo("HOARDED")
        assertThat(file.kind).isEqualTo("CODE")
        assertThat(file.hotspot).isEqualTo(0.7)
        assertThat(file.abandonedShare).isEqualTo(0.6)
        assertThat(file.coupledWith).containsExactly("b.kt")
        assertThat(file.reasons).containsExactly("Only one person who knows it is left.")
        assertThat(sent.captured.toString()).doesNotContain("ada", "ben", "cy")
        assertThat(plan.files).isEqualTo(1)
        assertThat(plan.questions.single().text).isEqualTo("Why?")
        assertThat(plan.answeredByRecords).hasSize(1)
    }

    @Test
    fun `only the riskiest files are sent, at most forty`() = runTest {
        every { risks.riskMap(project, request) } returns mapOf(*Array(50) { risk("f$it.kt") })
        val sent = slot<AiInterviewRequest>()
        coEvery { ai.planInterview(capture(sent)) } returns AiInterviewResponse(emptyList())

        assertThat(service.plan(project, request, 3).files).isEqualTo(3)
        assertThat(sent.captured.files.map { it.path }).containsExactly("f0.kt", "f1.kt", "f2.kt")
        assertThat(service.plan(project, request, 100).files).isEqualTo(40)
    }

    @Test
    fun `a departure that puts nothing at risk asks the AI service nothing`() = runTest {
        every { risks.riskMap(project, request) } returns mapOf()

        val plan = service.plan(project, request, 10)

        assertThat(plan.questions).isEmpty()
        assertThat(plan.files).isZero()
        coVerify(exactly = 0) { ai.planInterview(any()) }
    }
}
