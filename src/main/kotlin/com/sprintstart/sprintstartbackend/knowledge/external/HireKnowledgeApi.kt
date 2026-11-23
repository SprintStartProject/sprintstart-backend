package com.sprintstart.sprintstartbackend.knowledge.external

import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.knowledge.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/** What a hire's assistant does with the project's human knowledge: serves it, and asks for more. */
interface HireKnowledgeApi {
    /**
     * Escalates [question] to the PMs of [projectId], as the hire.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 for a blank question, 404 when the
     *   hire is not on the project.
     */
    fun escalate(authId: AuthId, projectId: ProjectId, question: String): KnowledgeRequestResponse

    /**
     * The answers PMs gave on [userId]'s projects that match [query], best match first, worded exactly as
     * the PM wrote them.
     */
    fun searchAnswers(userId: UserId, query: String): List<CanonicalAnswerResponse>
}
