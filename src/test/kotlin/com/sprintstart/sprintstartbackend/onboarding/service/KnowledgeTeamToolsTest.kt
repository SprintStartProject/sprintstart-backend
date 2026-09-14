package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.EscalationHireResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class KnowledgeTeamToolsTest {
    private val knowledgeBaseService: KnowledgeBaseService = mockk()
    private val tools = KnowledgeTeamTools(knowledgeBaseService)

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun call(name: String) = BuddyToolCallDto(id = "c1", name = name)

    @Test
    fun `is the knowledge area and handles exactly its two reads`() {
        assertThat(tools.area).isEqualTo(TeamArea.KNOWLEDGE)
        assertThat(tools.toolSpecs().map { it.name })
            .containsExactly(KnowledgeTeamTools.LIST_OPEN_ESCALATIONS, KnowledgeTeamTools.LIST_CANONICAL_ANSWERS)
        assertThat(tools.handles("answer_escalation")).isFalse()
    }

    @Test
    fun `lists the project's open questions with the id to act on and who asked`() {
        val requestId = UUID.randomUUID()
        every { knowledgeBaseService.listOpen(projectId) } returns listOf(
            KnowledgeRequestResponse(
                id = requestId,
                projectId = projectId,
                hireId = UUID.randomUUID(),
                question = "How do we deploy?",
                status = KnowledgeRequestStatus.OPEN,
                createdAt = Instant.parse("2026-09-10T08:00:00Z"),
                answeredAt = null,
                answer = null,
                hire = EscalationHireResponse(
                    userId = UUID.randomUUID(),
                    displayName = "Sam Rivera",
                    profileIcon = null,
                    currentPhase = "First contribution",
                    currentStep = null,
                    progressPercentage = 40.0,
                ),
            ),
        )

        val result = tools.execute(call(KnowledgeTeamTools.LIST_OPEN_ESCALATIONS), context)

        assertThat(result)
            .contains("“How do we deploy?” [request_id: $requestId]")
            .contains("asked by Sam Rivera, who is currently in First contribution")
            .contains("2026-09-10")
        verify { knowledgeBaseService.listOpen(projectId) }
    }

    @Test
    fun `says so when nobody is waiting for an answer`() {
        every { knowledgeBaseService.listOpen(projectId) } returns emptyList()

        assertThat(tools.execute(call(KnowledgeTeamTools.LIST_OPEN_ESCALATIONS), context))
            .contains("Nobody on this project")
    }

    @Test
    fun `lists canonical answers in full with the id to edit them by`() {
        val answerId = UUID.randomUUID()
        every { knowledgeBaseService.listAnswers(projectId) } returns listOf(
            CanonicalAnswerResponse(
                id = answerId,
                projectId = projectId,
                question = "How do we deploy?",
                answer = "Merge to dev; the pipeline deploys staging.",
                authorId = UUID.randomUUID(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        val result = tools.execute(call(KnowledgeTeamTools.LIST_CANONICAL_ANSWERS), context)

        assertThat(result)
            .contains("Q: How do we deploy? [answer_id: $answerId]")
            .contains("A: Merge to dev; the pipeline deploys staging.")
    }
}
