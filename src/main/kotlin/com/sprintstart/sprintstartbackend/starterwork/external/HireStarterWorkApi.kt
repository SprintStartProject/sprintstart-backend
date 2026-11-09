package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.model.PlacementTopic
import com.sprintstart.sprintstartbackend.starterwork.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import java.time.Instant

/**
 * One hire's starter work as the board, the assistants and the metrics read it: the task they claimed,
 * the tasks that suit them, their competencies and the orientation for their task.
 */
interface HireStarterWorkApi {
    /** The task [hireId] claimed on [projectId], or null when they have claimed none. */
    fun currentTaskOf(hireId: UserId, projectId: ProjectId): StarterWorkTaskProposalResponse?

    /** The live tasks on [projectId] ranked by how well they suit [hireId], best first. */
    fun rankedTasksFor(hireId: UserId, projectId: ProjectId): List<RankedStarterWorkTaskResponse>

    /** Every competency [userId] has a level in. The ledger is global, not per project. */
    fun competenciesOf(userId: UserId): List<MyCompetencyResponse>

    /** When [hireId] claimed their goal on [projectId], or null when they have none there. */
    fun goalClaimedAt(hireId: UserId, projectId: ProjectId): Instant?

    /** Orientation for the task [hireId] is on; 404 when they are not a member of [projectId]. */
    suspend fun orientationFor(hireId: UserId, projectId: ProjectId): MyOrientationResponse

    /** The competencies an assessment could place [hireId] on, most useful first. */
    fun placementTopicsFor(hireId: UserId): List<PlacementTopic>

    /** The label of the competency [competencyKey] names, or null when there is no such competency. */
    fun competencyLabel(competencyKey: String): String?
}
