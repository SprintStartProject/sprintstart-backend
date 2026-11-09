package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.SourceId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.external.StarterWorkPoolApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.starterwork.repository.StarterWorkTaskProposalRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [StarterWorkPoolApi]: the starter-work pool as one project sees it, for an assistant that names a task by id.
 *
 * The pool has no project of its own: a task belongs to a project through the repository its issue comes
 * from, so every read here goes through [StarterWorkScope], and a task from elsewhere reads as absent.
 */
@Component
class StarterWorkPool(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkScope: StarterWorkScope,
) : StarterWorkPoolApi {
    /** The task [taskId] names, whatever its status, if its repository is linked to [projectId]. */
    @Transactional(readOnly = true)
    override fun taskOn(taskId: StarterWorkTaskId?, projectId: ProjectId): StarterWorkTaskProposalResponse? =
        taskId
            ?.let { starterWorkTaskProposalRepository.findById(it.value).orElse(null) }
            ?.takeIf { starterWorkScope.covers(SourceId(it.sourceId), projectId.value) }
            ?.toResponse()

    /** The live pool task [taskId] names, if its repository is linked to [projectId]. */
    override fun liveOn(taskId: StarterWorkTaskId?, projectId: ProjectId): StarterWorkTaskProposalResponse? =
        taskOn(taskId, projectId)?.takeIf { it.status == ProposalStatus.LIVE }

    /** The live tasks from [projectId]'s repositories, by title. */
    @Transactional(readOnly = true)
    override fun liveTasksOn(projectId: ProjectId): List<StarterWorkTaskProposalResponse> =
        starterWorkScope
            .onProject(starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE), projectId.value) {
                it.sourceId
            }.sortedBy { it.title }
            .map { it.toResponse() }

    /** The live tasks from [projectId]'s repositories that nobody has reviewed, oldest first. */
    @Transactional(readOnly = true)
    override fun unreviewedTasksOn(projectId: ProjectId): List<StarterWorkTaskProposalResponse> =
        starterWorkScope
            .onProject(
                starterWorkTaskProposalRepository.findAllByStatusAndReviewedFalse(ProposalStatus.LIVE),
                projectId.value,
            ) { it.sourceId }
            .sortedBy { it.createdAt }
            .map { it.toResponse() }

    /** The status of the task made from the issue [source] names, or null when none was. */
    @Transactional(readOnly = true)
    override fun statusOf(source: SourceId): ProposalStatus? =
        starterWorkTaskProposalRepository.findBySourceId(source.value)?.status

    override fun covers(sourceId: SourceId, projectId: ProjectId): Boolean =
        starterWorkScope.covers(sourceId, projectId.value)
}
