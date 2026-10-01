package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardPullRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CompetencyProgressContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.MemoryRecapContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.OpenPullRequestsContent
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * What the live cards say, read from the services that own each fact.
 *
 * Kept beside [BoardService] rather than inside it: the board service decides which cards are on a
 * hire's board and when to load them, these only read the content a card of that kind shows. Every
 * function here is a read — loading a board must never be able to change anything.
 */
@Service
class LiveCardContentReader(
    private val openPullRequestReader: OpenPullRequestReader,
    private val currentTaskReader: CurrentTaskReader,
    private val myCompetencyService: MyCompetencyService,
    private val buddySessionRepository: BuddySessionRepository,
) {
    /**
     * The hire's ledger, split at the bar rather than summed into a percentage.
     *
     * The same read and the same level-0 exclusion as the buddy's `get_my_competencies` tool.
     * Level 0 means "asked, saw no evidence" — a placement, not a competency — so it is
     * filtered out here. The ledger is global, not per project.
     */
    fun competencyProgress(userId: UUID): CompetencyProgressContent {
        val (held, inProgress) = myCompetencyService
            .getCompetenciesForUser(userId)
            .filter { it.level > 0 }
            .partition { it.level >= it.targetLevel }
        return CompetencyProgressContent(
            held = held.map { it.toBoardResponse() },
            inProgress = inProgress.map { it.toBoardResponse() },
        )
    }

    /**
     * What the mentor remembers, read and never written.
     *
     * Not [BuddyService.getOrCreateSession] — hydrating a card must not create a session.
     */
    fun memoryRecap(userId: UUID): MemoryRecapContent {
        val session = buddySessionRepository.findByUserId(userId)
        return MemoryRecapContent(
            memory = session?.summary,
            messagesRemembered = session?.summarizedCount ?: 0,
        )
    }

    /**
     * The task the hire is on, read — never assigned.
     *
     * Read through [CurrentTaskReader], not `TaskZeroService.getForHire`, which assigns on
     * read. Hydration runs on every page load, so it must not be able to hand out a task.
     *
     * A card with no task on it is a real state and says so.
     */
    fun currentTask(userId: UUID, projectId: UUID): CurrentTaskContent {
        val task = currentTaskReader.currentTaskFor(userId, projectId)
        return CurrentTaskContent(
            taskId = task?.id,
            title = task?.title,
            summary = task?.summary,
            url = task?.sourceUrl,
            // True for a goal the hire claimed, false for a Task 0 they were handed.
            chosen = task != null && currentTaskReader.isClaimedGoal(userId, projectId),
            // Reconciliation moves a proposal to STALE when its issue closes at the source, so the
            // card can say so without a lookup of its own.
            closedAtSource = task?.status == ProposalStatus.STALE,
        )
    }

    fun openPullRequests(member: ProjectMember, projectId: UUID): OpenPullRequestsContent {
        val login = member.githubLogin
        val open = openPullRequestReader.openFor(projectId, login)
        return OpenPullRequestsContent(
            pullRequests = open.map { pullRequest ->
                BoardPullRequestResponse(
                    artifactId = pullRequest.artifactId,
                    number = pullRequest.number,
                    title = pullRequest.title,
                    url = pullRequest.sourceUrl,
                    waitingHours = openPullRequestReader.waitingHours(pullRequest),
                )
            },
            attributionMissing = login.isNullOrBlank(),
        )
    }
}
