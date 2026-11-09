package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.SourceId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse

/**
 * The starter-work pool as one project sees it, for team mode.
 *
 * A task belongs to every project its repository is linked to, so a task from elsewhere reads as absent.
 */
interface StarterWorkPoolApi {
    /** The task [taskId] names, whatever its status, if its repository is linked to [projectId]. */
    fun taskOn(taskId: StarterWorkTaskId?, projectId: ProjectId): StarterWorkTaskProposalResponse?

    /** The live pool task [taskId] names, if its repository is linked to [projectId]. */
    fun liveOn(taskId: StarterWorkTaskId?, projectId: ProjectId): StarterWorkTaskProposalResponse?

    /** The live tasks from [projectId]'s repositories, by title. */
    fun liveTasksOn(projectId: ProjectId): List<StarterWorkTaskProposalResponse>

    /** The live tasks from [projectId]'s repositories that nobody has reviewed, oldest first. */
    fun unreviewedTasksOn(projectId: ProjectId): List<StarterWorkTaskProposalResponse>

    /** The status of the task made from the issue [source] names, or null when none was. */
    fun statusOf(source: SourceId): ProposalStatus?

    /** Whether the task or issue [sourceId] names belongs to [projectId]. */
    fun covers(sourceId: SourceId, projectId: ProjectId): Boolean
}
