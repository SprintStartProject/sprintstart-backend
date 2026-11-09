package com.sprintstart.sprintstartbackend.knowledge.service

import com.sprintstart.sprintstartbackend.knowledge.external.model.AnswerEdit
import com.sprintstart.sprintstartbackend.knowledge.external.model.EscalationAnswer
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CanonicalAnswerId
import com.sprintstart.sprintstartbackend.shared.ids.KnowledgeRequestId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertSame

/** The knowledge facades only unwrap ids and value objects and hand each call to the knowledge base. */
class KnowledgeApiServicesTest {
    private val knowledgeBaseService: KnowledgeBaseService = mockk()
    private val knowledgeLookup: KnowledgeLookup = mockk()

    private val hire = HireKnowledgeService(knowledgeBaseService)
    private val review = KnowledgeReviewService(knowledgeBaseService, knowledgeLookup)

    private val id = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|pm"

    @Test
    fun `the hire's assistant escalates and searches through the knowledge base`() {
        every { knowledgeBaseService.escalate(authId, projectId, "How do we deploy?") } returns mockk()
        every { knowledgeBaseService.searchForUser(id, "deploy") } returns listOf(mockk())

        assertSame(
            knowledgeBaseService.escalate(authId, projectId, "How do we deploy?"),
            hire.escalate(AuthId(authId), ProjectId(projectId), "How do we deploy?"),
        )
        assertSame(knowledgeBaseService.searchForUser(id, "deploy"), hire.searchAnswers(UserId(id), "deploy"))
    }

    @Test
    fun `team mode reads, answers, dismisses and rewords on one project`() {
        val seen = Instant.parse("2026-10-20T12:00:00Z")
        every { knowledgeBaseService.listOpen(projectId) } returns listOf(mockk())
        every { knowledgeBaseService.listAnswers(projectId) } returns listOf(mockk())
        every { knowledgeLookup.openRequestOn(KnowledgeRequestId(id), ProjectId(projectId)) } returns mockk()
        every { knowledgeLookup.answerOn(CanonicalAnswerId(id), ProjectId(projectId)) } returns mockk()
        every { knowledgeBaseService.answerOpenOn(authId, projectId, id, "Merge to dev.", "Deploying?") } returns
            mockk()
        every { knowledgeBaseService.dismissOpenOn(projectId, id) } just runs
        every { knowledgeBaseService.editAnswerIfUnchanged(authId, projectId, id, "Q", "A", seen) } returns mockk()

        assertSame(knowledgeBaseService.listOpen(projectId), review.openRequests(ProjectId(projectId)))
        assertSame(knowledgeBaseService.listAnswers(projectId), review.answers(ProjectId(projectId)))
        assertSame(
            knowledgeLookup.openRequestOn(KnowledgeRequestId(id), ProjectId(projectId)),
            review.openRequestOn(KnowledgeRequestId(id), ProjectId(projectId)),
        )
        assertSame(
            knowledgeLookup.answerOn(CanonicalAnswerId(id), ProjectId(projectId)),
            review.answerOn(CanonicalAnswerId(id), ProjectId(projectId)),
        )
        assertSame(
            knowledgeBaseService.answerOpenOn(authId, projectId, id, "Merge to dev.", "Deploying?"),
            review.answerOpen(
                AuthId(authId),
                ProjectId(projectId),
                KnowledgeRequestId(id),
                EscalationAnswer(text = "Merge to dev.", question = "Deploying?"),
            ),
        )
        review.dismissOpen(ProjectId(projectId), KnowledgeRequestId(id))
        assertSame(
            knowledgeBaseService.editAnswerIfUnchanged(authId, projectId, id, "Q", "A", seen),
            review.editAnswerIfUnchanged(
                AuthId(authId),
                ProjectId(projectId),
                CanonicalAnswerId(id),
                AnswerEdit("Q", "A", seen),
            ),
        )

        verify { knowledgeBaseService.dismissOpenOn(projectId, id) }
    }
}
