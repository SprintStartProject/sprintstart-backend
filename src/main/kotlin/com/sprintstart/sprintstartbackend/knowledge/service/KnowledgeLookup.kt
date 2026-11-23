package com.sprintstart.sprintstartbackend.knowledge.service

import com.sprintstart.sprintstartbackend.knowledge.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.toResponse
import com.sprintstart.sprintstartbackend.knowledge.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.knowledge.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.shared.ids.CanonicalAnswerId
import com.sprintstart.sprintstartbackend.shared.ids.KnowledgeRequestId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Finds one escalation or one canonical answer of a project, for an assistant that names it by id.
 *
 * Anything on another project reads as absent, so an id taken from somewhere else proves nothing.
 */
@Component
class KnowledgeLookup(
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val canonicalAnswerRepository: CanonicalAnswerRepository,
) {
    /** The request [requestId] names, if it is still open and on [projectId]. */
    @Transactional(readOnly = true)
    fun openRequestOn(requestId: KnowledgeRequestId?, projectId: ProjectId): KnowledgeRequestResponse? =
        requestId
            ?.let { knowledgeRequestRepository.findById(it.value).orElse(null) }
            ?.takeIf { it.projectId == projectId.value && it.status == KnowledgeRequestStatus.OPEN }
            ?.toResponse(answer = null)

    /** The canonical answer [answerId] names, if it is on [projectId]. */
    @Transactional(readOnly = true)
    fun answerOn(answerId: CanonicalAnswerId?, projectId: ProjectId): CanonicalAnswerResponse? =
        answerId
            ?.let { canonicalAnswerRepository.findById(it.value).orElse(null) }
            ?.takeIf { it.projectId == projectId.value }
            ?.toResponse()
}
