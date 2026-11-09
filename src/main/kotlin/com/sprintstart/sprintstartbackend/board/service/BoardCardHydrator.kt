package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalReadApi
import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor
import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.board.external.MemoryRecapSource
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.model.entity.BoardDiagram
import com.sprintstart.sprintstartbackend.board.model.mapper.toLastChangeResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.ArrivalStepsContent
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCompetencyResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardPullRequestResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.CompetencyProgressContent
import com.sprintstart.sprintstartbackend.board.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.board.model.response.board.OpenPullRequestsContent
import com.sprintstart.sprintstartbackend.path.external.model.ResolvedPathStep
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * What each card on a hire's board currently says.
 *
 * Each card's content is read from the same service the equivalent buddy tool reads. Nothing is
 * copied onto the card row, so a card always says what its source says now.
 */
@Component
class BoardCardHydrator(
    private val openPullRequestReader: OpenPullRequestReader,
    private val hireStarterWork: HireStarterWorkApi,
    private val memoryRecapSource: MemoryRecapSource,
    private val boardDiagramService: BoardDiagramService,
    private val arrivalSteps: ArrivalReadApi,
) {
    /**
     * One card as the board shows it, its content read live.
     *
     * A whole board passes what it read once for every card — the diagrams, the hire's arrival
     * steps and path steps, one starter-work ranking — so the board pays for each read once; a
     * single card rendered on its own reads its arrival steps and ranking itself.
     */
    fun render(
        card: BoardCard,
        member: ProjectMember,
        projectId: ProjectId,
        shared: SharedBoardReads = standaloneReads(member, projectId),
    ): BoardCardResponse = BoardCardResponse(
        id = card.id,
        kind = card.kind,
        owner = card.owner,
        position = card.position,
        placedAt = card.placedAt,
        content = hydrate(card, member, projectId, shared),
        lastChange = card.toLastChangeResponse(),
    )

    /** A single card rendered on its own reads for itself; a whole board shares one read of each. */
    private fun standaloneReads(member: ProjectMember, projectId: ProjectId) = SharedBoardReads(
        diagram = null,
        arrivalSteps = arrivalSteps.forHire(UserId(member.userId)),
        pathSteps = emptyMap(),
        matches = { hireStarterWork.rankedTasksFor(UserId(member.userId), projectId) },
    )

    private fun hydrate(
        card: BoardCard,
        member: ProjectMember,
        projectId: ProjectId,
        shared: SharedBoardReads,
    ): BoardCardContent = when (card.kind) {
        BoardCardKind.ARRIVAL_STEPS -> arrivalStepsContent(shared.arrivalSteps)
        BoardCardKind.OPEN_PULL_REQUESTS -> openPullRequestsContent(member, projectId)
        BoardCardKind.CURRENT_TASK -> currentTaskContent(member, projectId)
        BoardCardKind.SUGGESTED_TASKS, BoardCardKind.TASK_POOL ->
            rankedTasksContent(card.kind, member, projectId, shared.matches)
        BoardCardKind.COMPETENCY_PROGRESS -> competencyProgressContent(member)
        BoardCardKind.MEMORY_RECAP -> memoryRecapSource.latestRecap(UserId(member.userId))
        // The one card served from a cache: its content costs a model call.
        // [BoardDiagramService] owns whether that cache is still valid.
        BoardCardKind.DIAGRAM -> boardDiagramService.contentFor(card.subject.orEmpty(), shared.diagram)
        BoardCardKind.PATH_STEP -> pathStepContent(card.subject, shared.pathSteps)
        BoardCardKind.NOTE, BoardCardKind.LINK, BoardCardKind.CHECKLIST -> authoredContent(card.payload)
    }

    private fun rankedTasksContent(
        kind: BoardCardKind,
        member: ProjectMember,
        projectId: ProjectId,
        matches: () -> List<RankedStarterWorkTaskResponse>,
    ): BoardCardContent =
        if (kind == BoardCardKind.TASK_POOL) {
            BoardTaskCards.pool(
                matches(),
                currentTaskId = hireStarterWork.currentTaskOf(UserId(member.userId), projectId)?.id,
            )
        } else {
            BoardTaskCards.suggested(matches())
        }

    /**
     * What is still outstanding before this hire can work, counted by how each step was settled.
     *
     * The same read the hire's own `GET /me/arrival` serves, so the card and that endpoint cannot
     * disagree — the rule every other card here follows.
     *
     * Counted per rigor and never totalled. A step the system observed and a step somebody
     * ticked are different facts, and a single blended figure here would be meaningless.
     */
    private fun arrivalStepsContent(steps: List<ArrivalStepResponse>): ArrivalStepsContent {
        return ArrivalStepsContent(
            steps = steps,
            observedCount = steps.count { it.rigor == Rigor.OBSERVED },
            declaredCount = steps.count { it.rigor == Rigor.DECLARED },
            outstandingCount = steps.count { !it.settled },
        )
    }

    /**
     * The hire's ledger, split at the bar rather than summed into a percentage.
     *
     * The same read and the same level-0 exclusion as the buddy's `get_my_competencies` tool.
     * Level 0 means "asked, saw no evidence" — a placement, not a competency — so it is
     * filtered out here. The ledger is global, not per project.
     */
    private fun competencyProgressContent(member: ProjectMember): CompetencyProgressContent {
        val (held, inProgress) = hireStarterWork
            .competenciesOf(UserId(member.userId))
            .filter { it.level > 0 }
            .partition { it.level >= it.targetLevel }
        return CompetencyProgressContent(
            held = held.map { it.toBoardResponse() },
            inProgress = inProgress.map { it.toBoardResponse() },
        )
    }

    private fun MyCompetencyResponse.toBoardResponse() = BoardCompetencyResponse(
        competencyKey = competencyKey,
        label = label,
        level = level,
        targetLevel = targetLevel,
    )

    /**
     * The task the hire is on, read — never assigned.
     *
     * Read through [HireStarterWorkApi.currentTaskOf], the same read the task packet uses, so the card and the
     * packet cannot be about different tasks.
     *
     * A card with no task on it is a real state and says so.
     */
    private fun currentTaskContent(member: ProjectMember, projectId: ProjectId): CurrentTaskContent {
        val task = hireStarterWork.currentTaskOf(UserId(member.userId), projectId)
        return CurrentTaskContent(
            taskId = task?.id,
            title = task?.title,
            summary = task?.summary,
            url = task?.sourceUrl,
            // Reconciliation moves a proposal to STALE when its issue closes at the source, so the
            // card can say so without a lookup of its own.
            closedAtSource = task?.status == ProposalStatus.STALE,
        )
    }

    private fun openPullRequestsContent(
        member: ProjectMember,
        projectId: ProjectId,
    ): OpenPullRequestsContent {
        val login = member.githubLogin
        val open = openPullRequestReader.openFor(projectId.value, login)
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

/**
 * What a whole board reads once and hands to every card — its diagram, the hire's arrival steps and
 * path steps, one starter-work ranking — so the board pays for each read once.
 */
class SharedBoardReads(
    val diagram: BoardDiagram?,
    val arrivalSteps: List<ArrivalStepResponse>,
    val pathSteps: Map<UUID, ResolvedPathStep>,
    val matches: () -> List<RankedStarterWorkTaskResponse>,
)
