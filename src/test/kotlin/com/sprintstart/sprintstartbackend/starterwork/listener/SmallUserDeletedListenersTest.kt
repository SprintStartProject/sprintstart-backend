package com.sprintstart.sprintstartbackend.starterwork.listener

import com.sprintstart.sprintstartbackend.assistant.listener.ProposalUserDeletedListener
import com.sprintstart.sprintstartbackend.assistant.repository.BuddyActionProposalRepository
import com.sprintstart.sprintstartbackend.knowledge.listener.KnowledgeUserDeletedListener
import com.sprintstart.sprintstartbackend.knowledge.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.GithubHistoryPriorRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

/** The listeners that erase by user id alone, with nothing to order. */
class SmallUserDeletedListenersTest {
    private val userId = UUID.randomUUID()

    @Test
    fun `deleting a user erases what starter-work matching learned about them`() {
        val competencies: UserCompetencyStateRepository = mockk(relaxed = true)
        val goals: UserGoalRepository = mockk(relaxed = true)
        val priors: GithubHistoryPriorRepository = mockk(relaxed = true)

        StarterWorkUserDeletedListener(competencies, goals, priors).onUserDeleted(UserDeletedEvent(userId))

        verify { competencies.deleteAllByUserId(userId) }
        verify { goals.deleteAllByUserId(userId) }
        verify { priors.deleteAllByUserId(userId) }
    }

    @Test
    fun `deleting a user erases the questions they escalated`() {
        val requests: KnowledgeRequestRepository = mockk(relaxed = true)

        KnowledgeUserDeletedListener(requests).onUserDeleted(UserDeletedEvent(userId))

        verify { requests.deleteAllByHireId(userId) }
    }

    /** What an assistant offered a manager, and what they decided, is theirs and goes with the account. */
    @Test
    fun `deleting a user erases the actions proposed to them`() {
        val proposals: BuddyActionProposalRepository = mockk(relaxed = true)

        ProposalUserDeletedListener(proposals).onUserDeleted(UserDeletedEvent(userId))

        verify { proposals.deleteAllByUserId(userId) }
    }
}
