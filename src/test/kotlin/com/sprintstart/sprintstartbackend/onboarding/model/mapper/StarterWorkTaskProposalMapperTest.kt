package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StarterWorkTaskProposalMapperTest {
    @Test
    fun `carries reviewed, sourceHasAssignee and sourceCheckedAt onto the response`() {
        val checkedAt = Instant.parse("2026-01-01T00:00:00Z")
        val proposal = StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:1",
            title = "Fix typo",
            reviewed = true,
            sourceHasAssignee = true,
            sourceCheckedAt = checkedAt,
        )

        val response = proposal.toResponse()

        assertEquals(true, response.reviewed)
        assertEquals(true, response.sourceHasAssignee)
        assertEquals(checkedAt, response.sourceCheckedAt)
    }

    @Test
    fun `leaves sourceHasAssignee and sourceCheckedAt null when reconciliation has never looked`() {
        val proposal = StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:2",
            title = "Add tests",
            status = ProposalStatus.LIVE,
            reviewed = false,
        )

        val response = proposal.toResponse()

        assertEquals(false, response.reviewed)
        assertNull(response.sourceHasAssignee)
        assertNull(response.sourceCheckedAt)
    }
}
