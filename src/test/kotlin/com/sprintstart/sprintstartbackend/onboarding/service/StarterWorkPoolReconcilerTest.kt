package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IngestedIssue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterWorkPoolReconcilerTest {
    private val repository: StarterWorkTaskProposalRepository = mockk(relaxed = true)
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()

    private val reconciler = StarterWorkPoolReconciler(repository, artifactIngestionApi)

    init {
        // `save` is generic (`<S : T> save(S): S`), which a relaxed mock cannot infer — it hands
        // back a bare Object. The rows here are mutated in place, so echoing the argument is both
        // the simplest stub and the truthful one.
        every { repository.save(any<StarterWorkTaskProposal>()) } answers { firstArg() }
    }

    private fun proposal(
        sourceId: String = "github:acme/api:ISSUE:1",
        status: ProposalStatus = ProposalStatus.LIVE,
        hasAssignee: Boolean? = null,
    ) = StarterWorkTaskProposal(
        sourceId = sourceId,
        title = "Fix the thing",
        status = status,
    ).also { it.sourceHasAssignee = hasAssignee }

    private fun issue(
        sourceId: String = "github:acme/api:ISSUE:1",
        state: String? = "OPEN",
        hasAssignee: Boolean? = null,
    ) = IngestedIssue(
        sourceId = sourceId,
        tracker = "GITHUB",
        title = "Fix the thing",
        body = null,
        labels = emptyList(),
        sourceUrl = null,
        state = state,
        hasAssignee = hasAssignee,
        updatedAtSource = null,
    )

    private fun poolOf(vararg rows: StarterWorkTaskProposal) {
        every { repository.findAllByStatusIn(any()) } returns rows.toList()
    }

    @Nested
    inner class GoingStale {
        @Test
        fun `a live task whose issue was closed goes stale`() {
            val row = proposal(status = ProposalStatus.LIVE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = "CLOSED")

            val outcome = reconciler.reconcile()

            assertEquals(ProposalStatus.STALE, row.status)
            assertEquals(1, outcome.markedStale)
        }

        @Test
        fun `closed is matched however the tracker capitalises it`() {
            val row = proposal(status = ProposalStatus.LIVE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = "closed")

            reconciler.reconcile()

            assertEquals(ProposalStatus.STALE, row.status)
        }

        @Test
        fun `an unknown state is not closed`() {
            val row = proposal(status = ProposalStatus.LIVE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = null)

            val outcome = reconciler.reconcile()

            assertEquals(ProposalStatus.LIVE, row.status, "unknown must never empty the pool")
            assertEquals(0, outcome.markedStale)
        }

        @Test
        fun `an issue the corpus no longer holds is left alone`() {
            val row = proposal(status = ProposalStatus.LIVE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns null

            val outcome = reconciler.reconcile()

            assertEquals(ProposalStatus.LIVE, row.status)
            assertNull(row.sourceCheckedAt, "nothing was compared, so nothing was checked")
            assertEquals(0, outcome.markedStale)
            assertEquals(1, outcome.skipped, "a vanished source is a finding, not a silent no-op")
        }
    }

    @Nested
    inner class ComingBack {
        @Test
        fun `a stale task whose issue reopened returns to the pool`() {
            val row = proposal(status = ProposalStatus.STALE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = "OPEN")

            val outcome = reconciler.reconcile()

            assertEquals(ProposalStatus.LIVE, row.status)
            assertEquals(1, outcome.revived)
        }

        @Test
        fun `a stale task whose issue is still closed stays stale`() {
            val row = proposal(status = ProposalStatus.STALE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = "CLOSED")

            val outcome = reconciler.reconcile()

            assertEquals(ProposalStatus.STALE, row.status)
            assertEquals(0, outcome.revived)
        }

        @Test
        fun `an unknown state does not revive a stale task either`() {
            val row = proposal(status = ProposalStatus.STALE)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(state = null)

            reconciler.reconcile()

            assertEquals(ProposalStatus.STALE, row.status)
        }
    }

    @Nested
    inner class RejectionStaysRejected {
        @Test
        fun `rejected rows are never even loaded`() {
            poolOf()

            reconciler.reconcile()

            val statuses = slot<Collection<ProposalStatus>>()
            verify { repository.findAllByStatusIn(capture(statuses)) }
            assertTrue(
                ProposalStatus.REJECTED !in statuses.captured,
                "a pass that could touch a rejection would undo somebody's decision",
            )
        }
    }

    @Nested
    inner class AssigneeSignal {
        @Test
        fun `an assignee at the source is recorded on the row`() {
            val row = proposal(hasAssignee = null)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(hasAssignee = true)

            val outcome = reconciler.reconcile()

            assertEquals(true, row.sourceHasAssignee)
            assertEquals(1, outcome.assigneeChanged)
        }

        @Test
        fun `an unknown assignee never overwrites a definite one`() {
            val row = proposal(hasAssignee = true)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(hasAssignee = null)

            val outcome = reconciler.reconcile()

            assertEquals(true, row.sourceHasAssignee, "unknown is not the same as free")
            assertEquals(0, outcome.assigneeChanged)
        }

        @Test
        fun `an issue somebody let go of is recorded as free again`() {
            val row = proposal(hasAssignee = true)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(hasAssignee = false)

            reconciler.reconcile()

            assertEquals(false, row.sourceHasAssignee)
        }

        @Test
        fun `an unchanged assignee is not counted as a change`() {
            val row = proposal(hasAssignee = true)
            poolOf(row)
            every { artifactIngestionApi.getIssue(row.sourceId) } returns issue(hasAssignee = true)

            assertEquals(0, reconciler.reconcile().assigneeChanged)
        }
    }

    @Test
    fun `an empty pool costs nothing`() {
        poolOf()

        val outcome = reconciler.reconcile()

        assertEquals(StarterWorkPoolReconciler.Outcome(0, 0, 0, 0, 0), outcome)
        verify(exactly = 0) { artifactIngestionApi.getIssue(any()) }
    }

    @Test
    fun `a compared row records when it was checked`() {
        val row = proposal()
        poolOf(row)
        every { artifactIngestionApi.getIssue(row.sourceId) } returns issue()

        reconciler.reconcile()

        assertTrue(row.sourceCheckedAt != null)
    }
}
