package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import com.sprintstart.sprintstartbackend.insights.external.KnowledgeGapsRefresh
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class KnowledgeTeamActionsTest {
    private val knowledgeRequestRepository: KnowledgeRequestRepository = mockk()
    private val canonicalAnswerRepository: CanonicalAnswerRepository = mockk()
    private val knowledgeBaseService: KnowledgeBaseService = mockk(relaxed = true)
    private val insightsRefreshApi: InsightsRefreshApi = mockk()

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private val answer = AnswerEscalationAction(knowledgeRequestRepository, knowledgeBaseService)
    private val dismiss = DismissEscalationAction(knowledgeRequestRepository, knowledgeBaseService)
    private val edit = EditCanonicalAnswerAction(canonicalAnswerRepository, knowledgeBaseService)

    private fun call(name: String, vararg args: Pair<String, String>) =
        BuddyToolCallDto(
            id = "c1",
            name = name,
            arguments = JsonObject(args.associate { it.first to JsonPrimitive(it.second) }),
        )

    private fun request(
        onProject: UUID = projectId,
        status: KnowledgeRequestStatus = KnowledgeRequestStatus.OPEN,
    ): KnowledgeRequest {
        val request = KnowledgeRequest(
            projectId = onProject,
            hireId = UUID.randomUUID(),
            question = "How do we deploy?",
        )
        request.status = status
        every { knowledgeRequestRepository.findById(request.id) } returns Optional.of(request)
        return request
    }

    private fun canonical(onProject: UUID = projectId): CanonicalAnswer {
        val stored = CanonicalAnswer(
            projectId = onProject,
            question = "How do we deploy?",
            answer = "Merge to dev.",
            authorId = UUID.randomUUID(),
        )
        every { canonicalAnswerRepository.findById(stored.id) } returns Optional.of(stored)
        return stored
    }

    private fun TeamActionDraft.proposed(): TeamActionDraft.Proposed {
        assertThat(this).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return this as TeamActionDraft.Proposed
    }

    // --- answer_escalation --------------------------------------------------------------------------

    /** The answer service loads by id and never checks the project, so the action has to. */
    @Test
    fun `answering refuses a question from another project`() {
        val elsewhere = request(onProject = UUID.randomUUID())

        val draft = answer.draft(
            call("answer_escalation", "request_id" to elsewhere.id.toString(), "answer" to "Merge to dev."),
            context,
        )

        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
    }

    @Test
    fun `answering refuses a question that is no longer open`() {
        val answered = request(status = KnowledgeRequestStatus.ANSWERED)

        val draft = answer.draft(
            call("answer_escalation", "request_id" to answered.id.toString(), "answer" to "Merge to dev."),
            context,
        )

        assertThat((draft as TeamActionDraft.Refused).reason).contains("not open on this project")
    }

    @Test
    fun `answering refuses without an answer to publish`() {
        val open = request()

        val draft = answer.draft(call("answer_escalation", "request_id" to open.id.toString()), context)

        assertThat((draft as TeamActionDraft.Refused).reason).contains("No answer")
    }

    /** The manager is agreeing to publish the answer, so the preview carries every word of it. */
    @Test
    fun `answering previews the whole answer and what publishing it means`() {
        val open = request()

        val draft = answer.draft(
            call(
                "answer_escalation",
                "request_id" to open.id.toString(),
                "answer" to "Merge to dev; the pipeline deploys staging within ten minutes.",
            ),
            context,
        )
        val proposed = draft.proposed()

        assertThat(proposed.preview)
            .contains("How do we deploy?")
            .contains("Merge to dev; the pipeline deploys staging within ten minutes.")
            .contains("canonical answer")
        assertThat(answer.risk).isEqualTo(BuddyProposalRisk.STANDARD)
    }

    @Test
    fun `answering refuses at confirm when the question was answered since the preview`() {
        val open = request()
        val draft = answer.draft(
            call("answer_escalation", "request_id" to open.id.toString(), "answer" to "Merge to dev."),
            context,
        )
        val proposed = draft.proposed()
        open.status = KnowledgeRequestStatus.ANSWERED

        assertThat(answer.recheck(proposed.params, context)).contains("answered or dismissed since")
    }

    @Test
    fun `answering publishes as the manager who confirmed`() = runTest {
        val open = request()
        val draft = answer.draft(
            call(
                "answer_escalation",
                "request_id" to open.id.toString(),
                "answer" to "Merge to dev.",
                "question" to "How do we deploy to staging?",
            ),
            context,
        )
        val proposed = draft.proposed()

        val result = answer.perform(proposed.params, context)

        verify {
            knowledgeBaseService.answerOpenOn(
                "auth|pm",
                projectId,
                open.id,
                "Merge to dev.",
                "How do we deploy to staging?",
            )
        }
        assertThat(result).contains("canonical answer")
    }

    // --- dismiss_escalation -------------------------------------------------------------------------

    @Test
    fun `dismissing is destructive and says the question goes unanswered`() {
        val open = request()

        val proposed = dismiss.draft(call("dismiss_escalation", "request_id" to open.id.toString()), context).proposed()

        assertThat(dismiss.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        assertThat(proposed.preview).contains("without answering it").contains("How do we deploy?")
    }

    @Test
    fun `dismissing refuses a question from another project`() {
        val elsewhere = request(onProject = UUID.randomUUID())

        val draft = dismiss.draft(call("dismiss_escalation", "request_id" to elsewhere.id.toString()), context)

        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
    }

    @Test
    fun `dismissing performs the dismissal of the stored question`() = runTest {
        val open = request()
        val proposed = dismiss.draft(call("dismiss_escalation", "request_id" to open.id.toString()), context).proposed()

        dismiss.perform(proposed.params, context)

        verify { knowledgeBaseService.dismissOpenOn(projectId, open.id) }
    }

    // --- edit_canonical_answer ----------------------------------------------------------------------

    @Test
    fun `editing refuses an answer from another project`() {
        val elsewhere = canonical(onProject = UUID.randomUUID())

        val draft = edit.draft(
            call("edit_canonical_answer", "answer_id" to elsewhere.id.toString(), "answer" to "Tag a release."),
            context,
        )

        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
    }

    @Test
    fun `editing refuses a change that changes nothing`() {
        val stored = canonical()

        val draft = edit.draft(
            call("edit_canonical_answer", "answer_id" to stored.id.toString(), "answer" to "Merge to dev."),
            context,
        )

        assertThat((draft as TeamActionDraft.Refused).reason).contains("Nothing would change")
    }

    @Test
    fun `editing previews the old and the new wording, keeping what was not given`() {
        val stored = canonical()

        val draft = edit.draft(
            call("edit_canonical_answer", "answer_id" to stored.id.toString(), "answer" to "Tag a release."),
            context,
        )
        val proposed = draft.proposed()

        assertThat(proposed.preview)
            .contains("Before:\nQ: How do we deploy?\nA: Merge to dev.")
            .contains("After:\nQ: How do we deploy?\nA: Tag a release.")
    }

    /** Overwriting words the manager never saw is refused, not done. */
    @Test
    fun `editing refuses at confirm when somebody changed the answer after the preview`() {
        val stored = canonical()
        val draft = edit.draft(
            call("edit_canonical_answer", "answer_id" to stored.id.toString(), "answer" to "Tag a release."),
            context,
        )
        val proposed = draft.proposed()
        stored.updatedAt = Instant.now().plusSeconds(5)

        assertThat(edit.recheck(proposed.params, context)).contains("changed that answer after")
    }

    @Test
    fun `editing an unchanged answer passes the recheck and saves the new wording`() = runTest {
        val stored = canonical()
        val draft = edit.draft(
            call("edit_canonical_answer", "answer_id" to stored.id.toString(), "answer" to "Tag a release."),
            context,
        )
        val proposed = draft.proposed()

        assertThat(edit.recheck(proposed.params, context)).isNull()
        edit.perform(proposed.params, context)

        verify {
            knowledgeBaseService.editAnswerIfUnchanged(
                "auth|pm",
                projectId,
                stored.id,
                "How do we deploy?",
                "Tag a release.",
                stored.updatedAt,
            )
        }
    }

    // --- refreshes ----------------------------------------------------------------------------------

    @Test
    fun `refreshing the FAQ is bulk and reports the groups it stored`() = runTest {
        val refresh = RefreshFaqAction(insightsRefreshApi)
        coEvery { insightsRefreshApi.refreshFaq(projectId) } returns 6

        val proposed = refresh.draft(call("refresh_faq"), context).proposed()
        val result = refresh.perform(proposed.params, context)

        assertThat(refresh.risk).isEqualTo(BuddyProposalRisk.BULK)
        assertThat(proposed.preview).contains("can take a while")
        assertThat(result).contains("6 recurring-question groups")
    }

    @Test
    fun `rescanning for gaps reports components and gaps for the turn's project`() = runTest {
        val refresh = RefreshKnowledgeGapsAction(insightsRefreshApi)
        coEvery { insightsRefreshApi.refreshKnowledgeGaps(projectId) } returns
            KnowledgeGapsRefresh(gapCount = 2, componentCount = 9)

        val result = refresh.perform(refresh.draft(call("refresh_knowledge_gaps"), context).proposed().params, context)

        assertThat(result).contains("9 components").contains("2 are missing documentation")
    }
}
