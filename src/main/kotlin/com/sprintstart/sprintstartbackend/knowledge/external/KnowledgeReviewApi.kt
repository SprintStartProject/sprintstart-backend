package com.sprintstart.sprintstartbackend.knowledge.external

import com.sprintstart.sprintstartbackend.knowledge.external.model.AnswerEdit
import com.sprintstart.sprintstartbackend.knowledge.external.model.EscalationAnswer
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CanonicalAnswerId
import com.sprintstart.sprintstartbackend.shared.ids.KnowledgeRequestId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId

/**
 * A project's escalations and canonical answers, as team mode reviews and answers them.
 *
 * Everything is scoped to one project: a request or answer from another project reads as absent, and a
 * write against one is refused, so an id taken from elsewhere proves nothing. Writes check their target
 * in the same transaction, so a confirm never lands on something that changed since its preview.
 */
interface KnowledgeReviewApi {
    /** The questions on [projectId] still waiting for a person, oldest first, with who asked them. */
    fun openRequests(projectId: ProjectId): List<KnowledgeRequestResponse>

    /** Every canonical answer on [projectId]. */
    fun answers(projectId: ProjectId): List<CanonicalAnswerResponse>

    /** The request [requestId] names, if it is still open and on [projectId]. */
    fun openRequestOn(requestId: KnowledgeRequestId?, projectId: ProjectId): KnowledgeRequestResponse?

    /** The canonical answer [answerId] names, if it is on [projectId]. */
    fun answerOn(answerId: CanonicalAnswerId?, projectId: ProjectId): CanonicalAnswerResponse?

    /**
     * Answers request [requestId] while it is still open on [projectId], as the PM [pmAuthId].
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for a blank answer, 404 when the
     *   request is not on the project, 409 when it was already answered or dismissed.
     */
    fun answerOpen(
        pmAuthId: AuthId,
        projectId: ProjectId,
        requestId: KnowledgeRequestId,
        answer: EscalationAnswer,
    ): CanonicalAnswerResponse

    /**
     * Dismisses request [requestId] while it is still open on [projectId].
     *
     * @throws org.springframework.web.server.ResponseStatusException 409 when it was already answered or dismissed.
     */
    fun dismissOpen(projectId: ProjectId, requestId: KnowledgeRequestId)

    /**
     * Rewords answer [answerId] on [projectId], as the PM [pmAuthId], unless it changed since the PM saw it.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for a blank question or answer, 409
     *   when the answer changed since, or is not on the project.
     */
    fun editAnswerIfUnchanged(
        pmAuthId: AuthId,
        projectId: ProjectId,
        answerId: CanonicalAnswerId,
        edit: AnswerEdit,
    ): CanonicalAnswerResponse
}
