package com.sprintstart.sprintstartbackend.knowledge.service

import com.sprintstart.sprintstartbackend.knowledge.external.HireKnowledgeApi
import com.sprintstart.sprintstartbackend.knowledge.external.KnowledgeReviewApi
import com.sprintstart.sprintstartbackend.knowledge.external.model.AnswerEdit
import com.sprintstart.sprintstartbackend.knowledge.external.model.EscalationAnswer
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CanonicalAnswerId
import com.sprintstart.sprintstartbackend.shared.ids.KnowledgeRequestId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service

/** [HireKnowledgeApi] over the knowledge base. */
@Service
class HireKnowledgeService(
    private val knowledgeBaseService: KnowledgeBaseService,
) : HireKnowledgeApi {
    override fun escalate(authId: AuthId, projectId: ProjectId, question: String): KnowledgeRequestResponse =
        knowledgeBaseService.escalate(authId.value, projectId.value, question)

    override fun searchAnswers(userId: UserId, query: String): List<CanonicalAnswerResponse> =
        knowledgeBaseService.searchForUser(userId.value, query)
}

/** [KnowledgeReviewApi] over the knowledge base and its project-scoped lookup. */
@Service
class KnowledgeReviewService(
    private val knowledgeBaseService: KnowledgeBaseService,
    private val knowledgeLookup: KnowledgeLookup,
) : KnowledgeReviewApi {
    override fun openRequests(projectId: ProjectId): List<KnowledgeRequestResponse> =
        knowledgeBaseService.listOpen(projectId.value)

    override fun answers(projectId: ProjectId): List<CanonicalAnswerResponse> =
        knowledgeBaseService.listAnswers(projectId.value)

    override fun openRequestOn(requestId: KnowledgeRequestId?, projectId: ProjectId): KnowledgeRequestResponse? =
        knowledgeLookup.openRequestOn(requestId, projectId)

    override fun answerOn(answerId: CanonicalAnswerId?, projectId: ProjectId): CanonicalAnswerResponse? =
        knowledgeLookup.answerOn(answerId, projectId)

    override fun answerOpen(
        pmAuthId: AuthId,
        projectId: ProjectId,
        requestId: KnowledgeRequestId,
        answer: EscalationAnswer,
    ): CanonicalAnswerResponse =
        knowledgeBaseService.answerOpenOn(
            pmAuthId = pmAuthId.value,
            projectId = projectId.value,
            requestId = requestId.value,
            answerText = answer.text,
            questionOverride = answer.question,
        )

    override fun dismissOpen(projectId: ProjectId, requestId: KnowledgeRequestId) =
        knowledgeBaseService.dismissOpenOn(projectId.value, requestId.value)

    override fun editAnswerIfUnchanged(
        pmAuthId: AuthId,
        projectId: ProjectId,
        answerId: CanonicalAnswerId,
        edit: AnswerEdit,
    ): CanonicalAnswerResponse =
        knowledgeBaseService.editAnswerIfUnchanged(
            pmAuthId = pmAuthId.value,
            projectId = projectId.value,
            answerId = answerId.value,
            question = edit.question,
            answer = edit.answer,
            seenUpdatedAt = edit.seenUpdatedAt,
        )
}
