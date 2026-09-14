package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IngestedIssue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class StarterWorkTeamActionsTest {
    private val repository: StarterWorkTaskProposalRepository = mockk()
    private val service: StarterWorkTaskProposalService = mockk(relaxed = true)
    private val taskZeroService: TaskZeroService = mockk(relaxed = true)
    private val reconciler: StarterWorkPoolReconciler = mockk()
    private val ingestion: ArtifactIngestionApi = mockk()
    private val scope: StarterWorkScope = mockk()

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private val review = MarkStarterWorkReviewedAction(repository, service, scope)
    private val reject = RejectStarterWorkAction(repository, service, scope)
    private val promote = PromoteCandidateAction(ingestion, repository, service, scope)
    private val taskZero = SetTaskZeroEligibleAction(repository, taskZeroService, scope)

    private val mineSource = "github:acme/shop:ISSUE:42"

    init {
        every { scope.covers(any(), projectId) } answers { firstArg<String>().startsWith("github:acme/shop:") }
        every { repository.findBySourceId(any()) } returns null
    }

    private fun call(name: String, vararg args: Pair<String, Any>) =
        BuddyToolCallDto(
            id = "c1",
            name = name,
            arguments = JsonObject(
                args.associate { (key, value) ->
                    val element: JsonElement = if (value is Boolean) JsonPrimitive(value) else JsonPrimitive("$value")
                    key to element
                },
            ),
        )

    private fun task(
        sourceId: String = mineSource,
        status: ProposalStatus = ProposalStatus.LIVE,
        reviewed: Boolean = false,
        taskZeroEligible: Boolean = false,
    ): StarterWorkTaskProposal {
        val task = StarterWorkTaskProposal(
            sourceId = sourceId,
            title = "Fix flaky login test",
            status = status,
            reviewed = reviewed,
            taskZeroEligible = taskZeroEligible,
        )
        every { repository.findById(task.id) } returns Optional.of(task)
        return task
    }

    private fun issue(sourceId: String = mineSource, state: String? = "OPEN", title: String? = "Fix flaky login test") =
        IngestedIssue(
            sourceId = sourceId,
            tracker = "GITHUB",
            title = title,
            body = null,
            labels = emptyList(),
            sourceUrl = "https://github.com/acme/shop/issues/42",
            state = state,
            hasAssignee = null,
            updatedAtSource = null,
        ).also { every { ingestion.getIssue(sourceId) } returns it }

    private fun TeamActionDraft.proposed(): TeamActionDraft.Proposed {
        assertThat(this).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return this as TeamActionDraft.Proposed
    }

    private fun TeamActionDraft.refusal(): String {
        assertThat(this).isInstanceOf(TeamActionDraft.Refused::class.java)
        return (this as TeamActionDraft.Refused).reason
    }

    // --- mark_starter_work_reviewed -----------------------------------------------------------------

    /** The review service loads by id and the pool has no project, so the action checks the repository. */
    @Test
    fun `reviewing refuses a task from a repository not linked to the project`() {
        val elsewhere = task(sourceId = "github:acme/billing:ISSUE:7")

        val reason = review.draft(call("mark_starter_work_reviewed", "task_id" to elsewhere.id), context).refusal()

        assertThat(reason).contains("not in this project's starter-work pool")
    }

    @Test
    fun `reviewing refuses a hand-authored task, which no project owns`() {
        val authored = task(sourceId = "authored:${UUID.randomUUID()}")

        review.draft(call("mark_starter_work_reviewed", "task_id" to authored.id), context).refusal()
    }

    @Test
    fun `reviewing refuses a task that already left the pool, or is already reviewed`() {
        val removed = task(status = ProposalStatus.REJECTED)
        val done = task(reviewed = true)

        review.draft(call("mark_starter_work_reviewed", "task_id" to removed.id), context).refusal()
        assertThat(review.draft(call("mark_starter_work_reviewed", "task_id" to done.id), context).refusal())
            .contains("already reviewed")
    }

    @Test
    fun `reviewing previews the task and refuses at confirm once it was removed`() {
        val open = task()
        val proposed = review.draft(call("mark_starter_work_reviewed", "task_id" to open.id), context).proposed()

        assertThat(proposed.preview).contains("“Fix flaky login test” (acme/shop)")
        assertThat(review.recheck(proposed.params, context)).isNull()

        open.status = ProposalStatus.REJECTED
        assertThat(review.recheck(proposed.params, context)).contains("left the starter-work pool since")
    }

    @Test
    fun `reviewing marks the stored task`() = runTest {
        val open = task()
        val proposed = review.draft(call("mark_starter_work_reviewed", "task_id" to open.id), context).proposed()

        review.perform(proposed.params, context)

        verify { service.markReviewed(open.id) }
    }

    // --- reject_starter_work ------------------------------------------------------------------------

    @Test
    fun `removing is destructive and says it is permanent and reaches every linked project`() {
        val open = task()

        val proposed = reject
            .draft(
                call("reject_starter_work", "task_id" to open.id, "reason" to "Needs production access."),
                context,
            ).proposed()

        assertThat(reject.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        assertThat(proposed.preview)
            .contains("for good")
            .contains("cannot be put back")
            .contains("Every project linked to acme/shop")
            .contains("Reason recorded: Needs production access.")
    }

    @Test
    fun `removing refuses a task from another project's repository`() {
        val elsewhere = task(sourceId = "github:acme/billing:ISSUE:7")

        reject.draft(call("reject_starter_work", "task_id" to elsewhere.id), context).refusal()
    }

    @Test
    fun `removing without a reason records none`() = runTest {
        val open = task()
        val proposed = reject.draft(call("reject_starter_work", "task_id" to open.id), context).proposed()

        reject.perform(proposed.params, context)

        verify { service.reject(open.id, null) }
    }

    // --- promote_candidate --------------------------------------------------------------------------

    @Test
    fun `promoting refuses an issue from a repository not linked to the project`() {
        issue(sourceId = "github:acme/billing:ISSUE:7")

        promote.draft(call("promote_candidate", "source_id" to "github:acme/billing:ISSUE:7"), context).refusal()
    }

    @Test
    fun `promoting refuses a closed issue and one that is not ingested`() {
        issue(state = "CLOSED")
        every { ingestion.getIssue("github:acme/shop:ISSUE:99") } returns null

        promote.draft(call("promote_candidate", "source_id" to mineSource), context).refusal()
        promote.draft(call("promote_candidate", "source_id" to "github:acme/shop:ISSUE:99"), context).refusal()
    }

    @Test
    fun `promoting refuses an issue already pooled or removed for good`() {
        issue()
        every { repository.findBySourceId(mineSource) } returns task(status = ProposalStatus.LIVE)
        assertThat(promote.draft(call("promote_candidate", "source_id" to mineSource), context).refusal())
            .contains("already in the starter-work pool")

        every { repository.findBySourceId(mineSource) } returns task(status = ProposalStatus.REJECTED)
        assertThat(promote.draft(call("promote_candidate", "source_id" to mineSource), context).refusal())
            .contains("cannot be put back")
    }

    /** A stale row is an issue that reopened; promoting it revives the row. */
    @Test
    fun `promoting offers an issue whose task went stale`() {
        issue()
        every { repository.findBySourceId(mineSource) } returns task(status = ProposalStatus.STALE)

        val proposed = promote
            .draft(
                call("promote_candidate", "source_id" to mineSource, "summary" to "Small and well scoped."),
                context,
            ).proposed()

        assertThat(proposed.preview)
            .contains("“Fix flaky login test” (acme/shop)")
            .contains("every project linked to acme/shop")
            .contains("Summary shown with it: Small and well scoped.")
    }

    @Test
    fun `promoting refuses at confirm when somebody pooled the issue since`() {
        issue()
        val proposed = promote.draft(call("promote_candidate", "source_id" to mineSource), context).proposed()
        every { repository.findBySourceId(mineSource) } returns task(status = ProposalStatus.LIVE)

        assertThat(promote.recheck(proposed.params, context))
            .contains("already in the starter-work pool")
            .contains("Nothing was added")
    }

    @Test
    fun `promoting sends the stored source id and summary`() = runTest {
        issue()
        val proposed = promote
            .draft(
                call("promote_candidate", "source_id" to mineSource, "summary" to "Small."),
                context,
            ).proposed()

        promote.perform(proposed.params, context)

        verify {
            service.promoteCandidate(
                PromoteStarterWorkCandidateRequest(sourceId = mineSource, summary = "Small."),
            )
        }
    }

    @Test
    fun `a promotion that loses the race to the unique source id is a readable refusal`() = runTest {
        issue()
        val proposed = promote.draft(call("promote_candidate", "source_id" to mineSource), context).proposed()
        every { service.promoteCandidate(any()) } throws DataIntegrityViolationException("duplicate source_id")

        val thrown = runCatching { promote.perform(proposed.params, context) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(ResponseStatusException::class.java)
        assertThat((thrown as ResponseStatusException).reason).contains("at the same moment")
    }

    // --- set_task_zero_eligible ---------------------------------------------------------------------

    @Test
    fun `flagging for Task 0 needs to know which way, and refuses a change that changes nothing`() {
        val flagged = task(taskZeroEligible = true)

        assertThat(taskZero.draft(call("set_task_zero_eligible", "task_id" to flagged.id), context).refusal())
            .contains("eligible: true")
        assertThat(
            taskZero
                .draft(call("set_task_zero_eligible", "task_id" to flagged.id, "eligible" to true), context)
                .refusal(),
        ).contains("already a Task 0 candidate")
    }

    /** Task 0 is picked from every flagged task, whichever project the hire is on. */
    @Test
    fun `flagging for Task 0 says a hire on any project may be given it`() = runTest {
        val open = task()
        val proposed = taskZero
            .draft(
                call("set_task_zero_eligible", "task_id" to open.id, "eligible" to true),
                context,
            ).proposed()

        assertThat(proposed.preview).contains("may be on any project")
        taskZero.perform(proposed.params, context)
        verify { taskZeroService.setEligibility(open.id, true) }
    }

    @Test
    fun `taking the Task 0 flag off accepts the model's string false`() = runTest {
        val flagged = task(taskZeroEligible = true)
        val proposed = taskZero
            .draft(
                call("set_task_zero_eligible", "task_id" to flagged.id, "eligible" to "false"),
                context,
            ).proposed()

        assertThat(proposed.preview).contains("already has it as their Task 0 keeps it")
        taskZero.perform(proposed.params, context)
        verify { taskZeroService.setEligibility(flagged.id, false) }
    }

    @Test
    fun `flagging refuses a task from another project's repository`() {
        val elsewhere = task(sourceId = "github:acme/billing:ISSUE:7")

        taskZero.draft(call("set_task_zero_eligible", "task_id" to elsewhere.id, "eligible" to true), context).refusal()
    }

    // --- reconcile_starter_work ---------------------------------------------------------------------

    @Test
    fun `reconciling is bulk, says it covers the whole pool, and reports what changed`() = runTest {
        val reconcile = ReconcileStarterWorkAction(reconciler)
        every { reconciler.reconcile() } returns
            StarterWorkPoolReconciler.Outcome(
                examined = 12,
                markedStale = 2,
                revived = 1,
                assigneeChanged = 0,
                skipped = 3,
            )

        val proposed = reconcile.draft(call("reconcile_starter_work"), context).proposed()
        val result = reconcile.perform(proposed.params, context)

        assertThat(reconcile.risk).isEqualTo(BuddyProposalRisk.BULK)
        assertThat(proposed.preview).contains("whole pool")
        assertThat(result)
            .contains("Checked 12 tasks")
            .contains("2 left")
            .contains("1 came back")
            .contains("3 could not be checked")
    }
}
